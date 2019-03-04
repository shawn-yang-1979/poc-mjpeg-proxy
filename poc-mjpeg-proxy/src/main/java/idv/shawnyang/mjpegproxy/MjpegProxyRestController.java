package idv.shawnyang.mjpegproxy;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import idv.shawnyang.mjpegproxy.MjpegProxy.VideoSource;
import lombok.extern.slf4j.Slf4j;

/**
 * This mjpeg proxy is inspired from a NodeJS mjpeg proxy. I like to have a java
 * version. So I made this PoC.
 * 
 * Reference:
 * https://github.com/legege/node-mjpeg-proxy/blob/master/mjpeg-proxy.js
 * 
 * @author SHAWN.SH.YANG
 *
 */
@Slf4j
@RestController
@RequestMapping(value = "/api/mjpeg-proxy")
public class MjpegProxyRestController {

	@Autowired
	private MjpegProxy mjpegProxy;

	@GetMapping("/camera-video")
	public void getCameraVideoV2(@RequestParam("url") String url, HttpServletResponse response) throws IOException {
		response.setStatus(200);
		VideoSource videoSource = mjpegProxy.getVideoSource(url);
		Map<String, List<String>> headers = videoSource.getHeaders();
		headers.entrySet().stream().forEach(//
				entry -> entry.getValue().stream().forEach(//
						value -> response.addHeader(entry.getKey(), value)));
		OutputStream out = response.getOutputStream();
		try {
			readAndWrite(videoSource, out);
		} catch (IOException e) {
			mjpegProxy.removeVideoSource(url);
		}
	}

	private void readAndWrite(VideoSource videoSource, OutputStream out) throws IOException {
		while (true) {
			List<byte[]> frame = videoSource.getFrame();
			if (frame == null || frame.isEmpty()) {
				break;
			}
			for (byte[] lineOfBytes : frame) {
				out.write(lineOfBytes);
			}
			/**
			 * Have to sleep 0.1 second for every frame otherwise firefox won't
			 * work. I don't understand why. But it works now.
			 * 
			 * Reference:
			 * https://github.com/FriesW/java-mjpeg-streamer/blob/master/
			 * Java/src/com/github/friesw/mjpegstreamer/MjpegStreamer.java
			 */
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				log.error(e.getMessage(), e);
				Thread.currentThread().interrupt();
			}
		}
	}

}