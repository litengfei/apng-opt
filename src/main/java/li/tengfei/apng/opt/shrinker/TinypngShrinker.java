package li.tengfei.apng.opt.shrinker;

import com.tinify.Tinify;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.impl.client.HttpClientBuilder;

import java.io.*;

/**
 * Png Optimizer offered by tinypng.com
 *
 * @author ltf
 * @since 16/12/5, 下午5:07
 */
public class TinypngShrinker implements Shrinker {
    public static void shrink(String inFile, String outFile, String apiKey) throws IOException {
        Tinify.setKey(apiKey);
        Tinify.fromFile(inFile).toFile(outFile);
    }

    @Override
    public boolean optimize(InputStream in, OutputStream out) {
        try {
            postPng(in);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    private String postPng(InputStream in) throws IOException {
        HttpClient http = HttpClientBuilder.create().build();
        HttpPost post = new HttpPost("https://tinypng.com/site/shrink");
        HttpEntity png = new InputStreamEntity(in);
        post.setEntity(png);
        HttpResponse response = http.execute(post);
        InputStream content = response.getEntity().getContent();
        BufferedReader reader = new BufferedReader(new InputStreamReader(content, "GB18030"));
        String line, result = "";
        while ((line = reader.readLine()) != null) result += line + "\n";
        return result;
    }


    private boolean download(String imgUrl, OutputStream os) {
        try {
            HttpClient http = HttpClientBuilder.create().build();
            RequestConfig config = RequestConfig.custom()
                    .setConnectionRequestTimeout(10000).setConnectTimeout(10000)
                    .setSocketTimeout(10000).build();
            HttpGet get = new HttpGet(imgUrl);
            get.setConfig(config);
            HttpResponse response = http.execute(get);

            byte[] buf = new byte[1024 * 16];
            InputStream content = response.getEntity().getContent();
            int len = -1;
            while ((len = content.read(buf)) > 0) os.write(buf, 0, len);
            os.flush();
            os.close();
            return true;
        } catch (Throwable e) {
        }
        return false;
    }
}
