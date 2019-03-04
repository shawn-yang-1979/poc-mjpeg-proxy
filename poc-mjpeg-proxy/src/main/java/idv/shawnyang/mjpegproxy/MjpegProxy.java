package idv.shawnyang.mjpegproxy;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class MjpegProxy {

	private Map<String, VideoSource> videoSourceByUrl = new HashMap<>();

	private VideoSource initVideoSource(String url) {
		VideoSource value = new VideoSource(url);
		value.start();
		return value;
	}

	public synchronized VideoSource getVideoSource(String url) {
		VideoSource videoSource = videoSourceByUrl.get(url);
		if (videoSource == null) {
			videoSource = this.initVideoSource(url);
			videoSourceByUrl.put(url, videoSource);
		} else {
			videoSource.increment();
		}
		return videoSource;
	}

	public synchronized void removeVideoSource(String url) {
		VideoSource videoSource = videoSourceByUrl.get(url);
		if (videoSource == null) {
			return;
		}
		int counter = videoSource.reduction();
		if (counter < 1) {
			videoSourceByUrl.remove(url);
		}
	}

	public static class VideoSource extends Thread {

		/**
		 * Don't have to use atomic integer here. This is only a practice.
		 * 
		 * Reference:
		 * 
		 * https://www.baeldung.com/java-thread-stop
		 * https://www.baeldung.com/java-atomic-variables
		 */
		private final AtomicInteger counter = new AtomicInteger(1);

		private int reduction() {
			while (true) {
				int existingValue = counter.get();
				int newValue = existingValue - 1;
				if (counter.compareAndSet(existingValue, newValue)) {
					return newValue;
				}
			}
		}

		private int increment() {
			while (true) {
				int existingValue = counter.get();
				int newValue = existingValue + 1;
				if (counter.compareAndSet(existingValue, newValue)) {
					return newValue;
				}
			}
		}

		@Getter
		private Map<String, List<String>> headers = new LinkedHashMap<>();

		private String boundary;

		private List<byte[]> frame;

		private InputStream inputStream;

		private VideoSource(String url) {
			super();
			try {
				URLConnection urlConn = new URL(url).openConnection();
				// change the timeout to taste, I like 1 second
				urlConn.setReadTimeout(1000);
				urlConn.connect();
				urlConn.getHeaderFields().entrySet().stream()//
						.filter(entry -> entry.getKey() != null)//
						.forEach(entry -> headers.put(entry.getKey(), entry.getValue()));
				String contentType = urlConn.getContentType();
				String boundaryString = StringUtils.substringAfter(contentType, "boundary=");
				boundary = "--" + boundaryString + "\r\n";
				this.inputStream = urlConn.getInputStream();
			} catch (IOException e) {
				throw new SourceConnectionException(url, e);
			}
		}

		@Override
		public void run() {
			try {
				List<byte[]> bufferFrame = new LinkedList<>();
				byte[] bufferBytes = null;
				int n = 0;
				int bite;
				while (counter.get() > 0) {
					bite = inputStream.read();
					if (bite < 0) {
						break;
					}
					if (n == 0) {
						bufferBytes = new byte[10000];
					}
					bufferBytes[n++] = (byte) bite;
					if (bite == '\n') {
						byte[] newLine = new byte[n];
						System.arraycopy(bufferBytes, 0, newLine, 0, n);
						String newLineString = new String(newLine);
						if (newLineString.equals(boundary) && !bufferFrame.isEmpty()) {
							frame = bufferFrame;
							bufferFrame = new LinkedList<>();
						}
						bufferFrame.add(newLine);
						n = 0;
					}
				}
			} catch (IOException e) {
				log.error(e.getMessage(), e);
			} finally {
				try {
					inputStream.close();
				} catch (IOException e) {
					log.error(e.getMessage(), e);
				}
			}
		}

		public List<byte[]> getFrame() {
			long maxRetryCount = 3;
			long retried = 0;
			while (retried < maxRetryCount) {
				if (this.frame == null || frame.isEmpty()) {
					retried++;
					try {
						Thread.sleep(100);
					} catch (InterruptedException e) {
						log.error(e.getMessage(), e);
						Thread.currentThread().interrupt();
					}
					continue;
				}
				return frame;
			}
			throw new FrameNotFoundException();
		}
	}

	public static class FrameNotFoundException extends RuntimeException {

		private static final long serialVersionUID = 1L;

	}

	public static class SourceConnectionException extends RuntimeException {

		private static final long serialVersionUID = 1L;

		public SourceConnectionException(String message, Throwable cause) {
			super(message, cause);
		}

	}

}
