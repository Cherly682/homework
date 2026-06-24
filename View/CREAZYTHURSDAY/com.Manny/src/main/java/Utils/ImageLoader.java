package Utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;

/**
 * ============================================================
 * 【图片加载工具 —— ImageLoader】
 * ============================================================
 */
public class ImageLoader {
    private static final Logger log = LoggerFactory.getLogger(ImageLoader.class);


    private ImageLoader() {}


    public static BufferedImage load(String filename) {
        // 拼接资源路径：所有图片统一放在 classpath 的 /images/ 目录下
        String resourcePath = "/images/" + filename;

        // try-with-resources：确保 InputStream 使用完毕后自动关闭
        // getResourceAsStream() 从 classpath 中读取资源文件
        try (InputStream in = ImageLoader.class.getResourceAsStream(resourcePath)) {
            if (in != null) {
                // ImageIO.read() 将输入流解析为 BufferedImage 对象
                BufferedImage img = ImageIO.read(in);
                log.debug("ImageLoader: 从 classpath 加载 {}", resourcePath);
                return img;
            }
        } catch (Exception e) {
            log.warn("ImageLoader: classpath 加载 {} 失败: {}", resourcePath, e.getMessage());
        }

        log.error("ImageLoader: 无法加载图片 {}", resourcePath);
        return null;
    }
}
