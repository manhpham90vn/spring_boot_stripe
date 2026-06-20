package com.manhpham.ticket.qr;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;

/** Render một chuỗi (QR token đã ký) thành ảnh QR PNG bằng ZXing — cho cổng/điện thoại quét. */
@Component
public class QrCodeRenderer {

    public byte[] pngFor(String text) {
        try {
            BitMatrix matrix = new MultiFormatWriter().encode(text, BarcodeFormat.QR_CODE, 256, 256);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Không render được QR", e);
        }
    }
}
