package care.dovetail.monitor;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import android.util.Log;
import android.util.Pair;

public class Utils {
	private static final String TAG = "Utils";

	private static final String CRLF = "\r\n";
	private static final String HYPHENS = "--";
	private static final String BOUNDARY =  "*****";


	public static Pair<Integer, String> uploadFile(String url, String contentType, byte data[])
			throws IOException {
		Log.v(TAG, String.format("Uploading %d bytes to %s", data.length, url));

		HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
		conn.setDoInput(true);
		conn.setDoOutput(true);
		conn.setUseCaches(false);
		conn.setRequestMethod("POST");

		// conn.setRequestProperty("Connection", "Keep-Alive");
		// conn.setRequestProperty("Authorization", ApiResponseTask.getAuthHeader(uuid, authToken));
		conn.setRequestProperty("ENCTYPE", "multipart/form-data");
		conn.setRequestProperty("Content-Type", "multipart/form-data;boundary=" + BOUNDARY);

		DataOutputStream out = new DataOutputStream(conn.getOutputStream());
		out.writeBytes(HYPHENS + BOUNDARY + CRLF);
		out.writeBytes("Content-Disposition: form-data; name=\"file\";filename=\"filename\"" + CRLF);
		out.writeBytes("Content-Type: " + contentType + CRLF);
		out.writeBytes(CRLF);
		out.write(data, 0, data.length);
		out.writeBytes(CRLF);

		out.writeBytes(HYPHENS + BOUNDARY + HYPHENS + CRLF);
		out.flush();
		out.close();

		int responseCode = conn.getResponseCode();
		StringBuilder response = new StringBuilder();

		try {
			String line;
			BufferedReader reader = new BufferedReader(
					new InputStreamReader((InputStream) conn.getContent()));
			while ((line = reader.readLine()) != null) {
				response.append(line);
			}
		} catch (IOException ex) {
			Log.w(TAG, ex);
			return Pair.create(responseCode, conn.getResponseMessage());
		} finally {
			conn.disconnect();
		}

		return Pair.create(responseCode, response.toString());
	}

	public static String join(String delimiter, String[] tokens) {
		if (tokens == null) {
			return null;
		}
		StringBuilder builder = new StringBuilder();
		for (String token : tokens) {
			builder.append(token).append(delimiter);
		}
		return builder.toString().replaceFirst(String.format("%s$", delimiter), "");
	}
}
