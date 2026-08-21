package ru.lightshow;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/** Загрузка картинки по ссылке и превращение её в цветные точки. */
public final class ImageLoader {

    private ImageLoader() {}

    /** Возвращает строки вида "RRGGBB,-,RRGGBB,..." где '-' — прозрачный пиксель. */
    public static List<String> load(String url, int maxW, int maxH, double alphaCut) throws Exception {
        if (!url.startsWith("http://") && !url.startsWith("https://")) throw new Exception("нужна прямая ссылка http/https");
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setRequestProperty("User-Agent", "LightShow/2.0 (Minecraft plugin)");
        con.setConnectTimeout(8000);
        con.setReadTimeout(15000);
        con.setInstanceFollowRedirects(true);
        if (con.getContentLength() > 12 * 1024 * 1024) throw new Exception("файл больше 12 МБ");
        BufferedImage img;
        try (InputStream in = con.getInputStream()) {
            img = ImageIO.read(in);
        }
        if (img == null) throw new Exception("это не картинка (нужен png/jpg/gif-кадр)");

        double k = Math.min((double) maxW / img.getWidth(), (double) maxH / img.getHeight());
        int w = Math.max(1, (int) Math.round(img.getWidth() * k));
        int h = Math.max(1, (int) Math.round(img.getHeight() * k));
        BufferedImage small = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = small.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(img, 0, 0, w, h, null);
        g.dispose();

        int cut = (int) (alphaCut * 255);
        List<String> rows = new ArrayList<String>(h);
        for (int y = 0; y < h; y++) {
            StringBuilder sb = new StringBuilder(w * 7);
            for (int x = 0; x < w; x++) {
                int argb = small.getRGB(x, y);
                if (x > 0) sb.append(',');
                if ((argb >>> 24) < cut) sb.append('-');
                else sb.append(String.format("%06X", argb & 0xFFFFFF));
            }
            rows.add(sb.toString());
        }
        return rows;
    }
}
