package idv.shawnyang.mjpegproxy;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "mjpeg-proxy")
public class MjpegProxyProperties {

	private int frameBufferSize = 10240;// default 10KB

}
