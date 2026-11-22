package com.danielhw99.user.service;

import java.awt.Color;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.Semaphore;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import com.danielhw99.common.error.ApiException;
import com.danielhw99.common.error.ErrorCode;

@Component
public class AvatarNormalizer {
    public static final int MAX_UPLOAD_BYTES = 5 * 1024 * 1024;
    private final Semaphore permits = new Semaphore(2);

    public byte[] normalize(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > MAX_UPLOAD_BYTES)
            throw invalid("사진은 비어 있지 않은 5MiB 이하 파일로 선택해 주세요.");
        String type = file.getContentType();
        if (!"image/jpeg".equals(type) && !"image/png".equals(type))
            throw invalid("JPEG 또는 PNG 사진을 선택해 주세요.");
        if (!permits.tryAcquire()) throw new ApiException(ErrorCode.RATE_LIMITED, 1L);
        try (var source = file.getInputStream()) {
            byte[] bytes = source.readNBytes(MAX_UPLOAD_BYTES + 1);
            if (bytes.length == 0 || bytes.length > MAX_UPLOAD_BYTES) throw invalid("사진은 5MiB 이하로 선택해 주세요.");
            try (var input = new MemoryCacheImageInputStream(new java.io.ByteArrayInputStream(bytes))) {
                var readers = ImageIO.getImageReaders(input);
                if (!readers.hasNext()) throw invalid("정상적인 JPEG 또는 PNG 사진을 선택해 주세요.");
                var reader = readers.next();
                try {
                    reader.setInput(input, true, true);
                    String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                    if (!("jpeg".equals(format) && "image/jpeg".equals(type))
                            && !("png".equals(format) && "image/png".equals(type)))
                        throw invalid("사진의 실제 형식과 파일 형식을 확인해 주세요.");
                    int width = reader.getWidth(0), height = reader.getHeight(0);
                    if (width < 1 || height < 1 || width > 8192 || height > 8192 || (long) width * height > 24_000_000)
                        throw invalid("사진 해상도는 최대 8192px, 2400만 화소 이하여야 해요.");
                    int side = Math.min(width, height);
                    var parameters = reader.getDefaultReadParam();
                    parameters.setSourceRegion(new Rectangle((width - side) / 2, (height - side) / 2, side, side));
                    int sample = Math.max(1, side / 512);
                    parameters.setSourceSubsampling(sample, sample, 0, 0);
                    var decoded = reader.read(0, parameters);
                    var normalized = new BufferedImage(256, 256, BufferedImage.TYPE_INT_RGB);
                    var graphics = normalized.createGraphics();
                    try {
                        graphics.setColor(Color.WHITE); graphics.fillRect(0, 0, 256, 256);
                        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                        graphics.drawImage(decoded, 0, 0, 256, 256, null);
                    } finally { graphics.dispose(); decoded.flush(); }
                    try {
                        var output = new ByteArrayOutputStream();
                        if (!ImageIO.write(normalized, "jpeg", output)) throw new IllegalStateException("JPEG encoder unavailable");
                        byte[] result = output.toByteArray();
                        if (result.length > 524288) throw invalid("사진을 줄여 다시 선택해 주세요.");
                        return result;
                    } finally { normalized.flush(); }
                } finally { reader.dispose(); }
            }
        } catch (IOException | IllegalArgumentException failure) {
            throw invalid("사진을 읽을 수 없어요. 정상적인 JPEG 또는 PNG 파일을 선택해 주세요.");
        } finally { permits.release(); }
    }
    private ApiException invalid(String message) { return new ApiException(ErrorCode.VALIDATION, "file", message); }
}
