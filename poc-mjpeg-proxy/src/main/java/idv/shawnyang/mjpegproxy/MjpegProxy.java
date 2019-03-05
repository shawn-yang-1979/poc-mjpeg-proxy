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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class MjpegProxy {

	@Autowired
	private MjpegProxyProperties mjpegProxyProperties;

	private Map<String, VideoSource> videoSourceByUrl = new HashMap<>();

	public synchronized VideoSource getVideoSource(String url) {
		VideoSource videoSource = videoSourceByUrl.get(url);
		if (videoSource == null) {
			videoSource = new VideoSource(url, mjpegProxyProperties.getFrameBufferSize());
			videoSource.increaseClientConnectionCount();
			videoSource.start();// start extract video frame from source
			videoSourceByUrl.put(url, videoSource);
		} else {
			videoSource.increaseClientConnectionCount();
		}
		return videoSource;
	}

	public synchronized void returnVideoSource(String url) {
		VideoSource videoSource = videoSourceByUrl.get(url);
		if (videoSource == null) {
			return;
		}
		int counter = videoSource.reduceClientConnectionCount();
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
		private final AtomicInteger clientConnectionCount = new AtomicInteger(0);

		private int reduceClientConnectionCount() {
			while (true) {
				int existingValue = clientConnectionCount.get();
				int newValue = existingValue - 1;
				if (clientConnectionCount.compareAndSet(existingValue, newValue)) {
					return newValue;
				}
			}
		}

		private int increaseClientConnectionCount() {
			while (true) {
				int existingValue = clientConnectionCount.get();
				int newValue = existingValue + 1;
				if (clientConnectionCount.compareAndSet(existingValue, newValue)) {
					return newValue;
				}
			}
		}

		@Getter
		private Map<String, List<String>> headers = new LinkedHashMap<>();

		private String boundary;

		private InputStream inputStream;

		private int frameBufferSize;

		private List<byte[]> frame;

		private VideoSource(String url, int frameBufferSize) {
			super();
			try {
				URLConnection urlConn = new URL(url).openConnection();
				urlConn.setReadTimeout(1000);
				urlConn.connect();

				// extract headers from source
				urlConn.getHeaderFields().entrySet().stream()//
						.filter(entry -> entry.getKey() != null)//
						.forEach(entry -> headers.put(entry.getKey(), entry.getValue()));

				// extract boundary from content type
				String contentType = urlConn.getContentType();
				String boundaryString = StringUtils.substringAfter(contentType, "boundary=");
				boundary = "--" + boundaryString + "\r\n";

				this.inputStream = urlConn.getInputStream();
			} catch (IOException e) {
				throw new VideoSourceConnectionException(url, e);
			}
			this.frameBufferSize = frameBufferSize;
		}

		/**
		 * Extract an "integral" frame from source and cache it in the "frame"
		 * attribute.
		 * 
		 * An integral frame means lines of bytes with a bytes of
		 * "--myboundary\r\n" as its first line.
		 */
		@Override
		public void run() {
			try {
				List<byte[]> tempFrame = new LinkedList<>();
				byte[] buffer = null;
				int n = 0;
				int bite;
				// stop this thread if not client is connecting
				while (clientConnectionCount.get() > 0) {
					bite = inputStream.read();
					if (bite < 0) {
						break;
					}
					if (n == 0) {
						buffer = new byte[frameBufferSize];
					}
					buffer[n++] = (byte) bite;
					if (bite == '\n') {
						byte[] newLine = new byte[n];
						System.arraycopy(buffer, 0, newLine, 0, n);
						String newLineString = new String(newLine);
						if (newLineString.equals(boundary) && !tempFrame.isEmpty()) {
							frame = tempFrame;
							tempFrame = new LinkedList<>();
						}
						tempFrame.add(newLine);
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
				// set frame to null, then client will get
				// FrameNotFoundException when they call getFrame()
				// Then, client will handle to returnVideoSource
				frame = null;
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

	public static class VideoSourceConnectionException extends RuntimeException {

		private static final long serialVersionUID = 1L;

		public VideoSourceConnectionException(String message, Throwable cause) {
			super(message, cause);
		}

	}

}
