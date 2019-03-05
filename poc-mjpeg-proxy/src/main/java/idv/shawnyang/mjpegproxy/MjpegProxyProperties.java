package idv.shawnyang.mjpegproxy;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

/**
 * @author SCOTT.SU
 * @Date 2018-12-04 上午10:30:37
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "mjpeg-proxy")
public class MjpegProxyProperties {

	private int frameBufferSize = 10240;// default 10KB

}
