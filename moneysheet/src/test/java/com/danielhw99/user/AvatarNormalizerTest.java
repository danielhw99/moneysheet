package com.danielhw99.user;

import static org.assertj.core.api.Assertions.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import com.danielhw99.common.error.ApiException;
import com.danielhw99.common.error.ErrorCode;
import com.danielhw99.user.service.AvatarNormalizer;

class AvatarNormalizerTest {
    private final AvatarNormalizer normalizer = new AvatarNormalizer();
    static MockMultipartFile image(String format, int width, int height) throws Exception {
        var image = new BufferedImage(width, height, format.equals("png") ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(image, format, bytes);
        return new MockMultipartFile("file", "original." + format, "image/" + (format.equals("jpg") ? "jpeg" : format), bytes.toByteArray());
    }
    @Test void normalizesPngAndJpegToSmallSquareJpeg() throws Exception {
        for (String format : new String[]{"png", "jpg"}) {
            byte[] result = normalizer.normalize(image(format, 800, 600));
            assertThat(result.length).isLessThanOrEqualTo(524288);
            assertThat(result[0]).isEqualTo((byte) 0xff); assertThat(result[1]).isEqualTo((byte) 0xd8);
            var decoded = ImageIO.read(new ByteArrayInputStream(result));
            assertThat(decoded.getWidth()).isEqualTo(256); assertThat(decoded.getHeight()).isEqualTo(256);
            assertThat(decoded.getColorModel().hasAlpha()).isFalse();
        }
    }
    @Test void flattensTransparentPixelsOntoWhite() throws Exception {
        var result = ImageIO.read(new ByteArrayInputStream(normalizer.normalize(image("png", 100, 100))));
        assertThat(result.getRGB(128, 128) & 0xffffff).isEqualTo(0xffffff);
    }
    @Test void rejectsEmptyOversizedForgedAndMismatchedFiles() throws Exception {
        var jpeg = image("jpg", 20, 20).getBytes();
        for (var file : new MockMultipartFile[]{
                new MockMultipartFile("file", "empty.png", "image/png", new byte[0]),
                new MockMultipartFile("file", "huge.png", "image/png", new byte[5 * 1024 * 1024 + 1]),
                new MockMultipartFile("file", "fake.png", "image/png", "<script>bad</script>".getBytes()),
                new MockMultipartFile("file", "wrong.png", "image/png", jpeg),
                new MockMultipartFile("file", "not-image.svg", "image/svg+xml", jpeg)}) {
            assertThatThrownBy(() -> normalizer.normalize(file)).isInstanceOf(ApiException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.VALIDATION);
        }
    }
    @Test void checksDimensionsBeforeDecodingPixels() throws Exception {
        var error = catchThrowableOfType(() -> normalizer.normalize(image("png", 8193, 1)), ApiException.class);
        assertThat(error.getFieldErrors()).extracting("field").containsExactly("file");
        assertThat(error.getFieldErrors()).extracting("message").anyMatch(message -> message.toString().contains("해상도"));
    }
}
