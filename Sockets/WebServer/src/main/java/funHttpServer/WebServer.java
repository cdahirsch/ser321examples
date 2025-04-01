/*
Simple Web Server in Java which allows you to call 
localhost:9000/ and show you the root.html webpage from the www/root.html folder
You can also do some other simple GET requests:
1) /random shows you a random picture (well random from the set defined)
2) json shows you the response as JSON for /random instead the html page
3) /file/filename shows you the raw file (not as HTML)
4) /multiply?num1=3&num2=4 multiplies the two inputs and responses with the result
5) /github?query=users/amehlhase316/repos (or other GitHub repo owners) will lead to receiving
   JSON which will for now only be printed in the console. See the todo below

The reading of the request is done "manually", meaning no library that helps making things a 
little easier is used. This is done so you see exactly how to pars the request and 
write a response back
*/

package funHttpServer;

import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Random;
import java.util.Map;
import java.util.LinkedHashMap;
import java.nio.charset.Charset;
import org.json.JSONArray;
import org.json.JSONObject;

class WebServer {
  public static void main(String args[]) {
    WebServer server = new WebServer(9000);
  }

  public WebServer(int port) {
    ServerSocket server = null;
    Socket sock = null;
    InputStream in = null;
    OutputStream out = null;

    try {
      server = new ServerSocket(port);
      while (true) {
        sock = server.accept();
        out = sock.getOutputStream();
        in = sock.getInputStream();
        byte[] response = createResponse(in);
        if (response == null) {
          response = ("HTTP/1.1 500 Internal Server Error\n\nServer failed to respond.").getBytes();
        }
        out.write(response);
        out.flush();
        in.close();
        out.close();
        sock.close();
      }
    } catch (IOException e) {
      e.printStackTrace();
    } finally {
      if (sock != null) {
        try {
          server.close();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    }
  }

  private final static HashMap<String, String> _images = new HashMap<>() {
    {
      put("streets", "https://iili.io/JV1pSV.jpg");
      put("bread", "https://iili.io/Jj9MWG.jpg");
    }
  };

  private Random random = new Random();

  public byte[] createResponse(InputStream inStream) {

    byte[] response = null;
    BufferedReader in = null;

    try {
      in = new BufferedReader(new InputStreamReader(inStream, "UTF-8"));
      String request = null;
      boolean done = false;
      while (!done) {
        String line = in.readLine();
        System.out.println("Received: " + line);
        if (line == null || line.equals(""))
          done = true;
        else if (line.startsWith("GET")) {
          int firstSpace = line.indexOf(" ");
          int secondSpace = line.indexOf(" ", firstSpace + 1);
          request = line.substring(firstSpace + 2, secondSpace);
        }
      }
      System.out.println("FINISHED PARSING HEADER\n");

      if (request == null) {
        response = "<html>Illegal request: no GET</html>".getBytes();
      } else {
        StringBuilder builder = new StringBuilder();

        if (request.length() == 0 || request.equals("/")) {
          String page = new String(readFileInBytes(new File("www/root.html")));
          page = page.replace("${links}", buildFileList());
          builder.append("HTTP/1.1 200 OK\n");
          builder.append("Content-Type: text/html; charset=utf-8\n\n");
          builder.append(page);

        } else if (request.equalsIgnoreCase("json")) {
          int index = random.nextInt(_images.size());
          String header = (String) _images.keySet().toArray()[index];
          String url = _images.get(header);
          builder.append("HTTP/1.1 200 OK\n");
          builder.append("Content-Type: application/json; charset=utf-8\n\n");
          builder.append("{");
          builder.append("\"header\":\"").append(header).append("\",");
          builder.append("\"image\":\"").append(url).append("\"");
          builder.append("}");

        } else if (request.equalsIgnoreCase("random")) {
          File file = new File("www/index.html");
          builder.append("HTTP/1.1 200 OK\n");
          builder.append("Content-Type: text/html; charset=utf-8\n\n");
          builder.append(new String(readFileInBytes(file)));

        } else if (request.contains("file/")) {
          File file = new File(request.replace("file/", ""));
          if (file.exists()) {
            builder.append("HTTP/1.1 200 OK\n");
            builder.append("Content-Type: text/html; charset=utf-8\n\n");
            builder.append("Would theoretically be a file but removed this part, you do not have to do anything with it for the assignment");
          } else {
            builder.append("HTTP/1.1 404 Not Found\n");
            builder.append("Content-Type: text/html; charset=utf-8\n\n");
            builder.append("File not found: " + file);
          }

        } else if (request.contains("multiply?")) {
          Map<String, String> query_pairs = new LinkedHashMap<>();
          try {
            query_pairs = splitQuery(request.replace("multiply?", ""));
            if (!query_pairs.containsKey("num1") || !query_pairs.containsKey("num2")) {
              builder.append("HTTP/1.1 400 Bad Request\n");
              builder.append("Content-Type: text/html; charset=utf-8\n\n");
              builder.append("Missing parameters. Usage: /multiply?num1=2&num2=3");
            } else {
              int num1 = Integer.parseInt(query_pairs.get("num1"));
              int num2 = Integer.parseInt(query_pairs.get("num2"));
              int result = num1 * num2;
              builder.append("HTTP/1.1 200 OK\n");
              builder.append("Content-Type: text/html; charset=utf-8\n\n");
              builder.append("Result is: " + result);
            }
          } catch (NumberFormatException e) {
            builder.append("HTTP/1.1 400 Bad Request\n");
            builder.append("Content-Type: text/html; charset=utf-8\n\n");
            builder.append("Invalid input. Both num1 and num2 must be integers.");
          }

        } else if (request.contains("github?")) {
          Map<String, String> query_pairs = new LinkedHashMap<>();
          try {
            query_pairs = splitQuery(request.replace("github?", ""));
            String query = query_pairs.get("query");
            if (query == null || query.isEmpty()) {
              builder.append("HTTP/1.1 400 Bad Request\n");
              builder.append("Content-Type: text/html; charset=utf-8\n\n");
              builder.append("Missing query parameter. Usage: /github?query=users/amehlhase316/repos");
            } else {
              String json = fetchURL("https://api.github.com/" + query);
              JSONArray repos = new JSONArray(json);
              builder.append("HTTP/1.1 200 OK\n");
              builder.append("Content-Type: text/html; charset=utf-8\n\n");
              for (int i = 0; i < repos.length(); i++) {
                JSONObject repo = repos.getJSONObject(i);
                String name = repo.getString("full_name");
                int id = repo.getInt("id");
                String owner = repo.getJSONObject("owner").getString("login");
                builder.append("Repo: ").append(name)
                        .append(" | ID: ").append(id)
                        .append(" | Owner: ").append(owner)
                        .append("<br>");
              }
            }
          } catch (Exception e) {
            builder.append("HTTP/1.1 500 Internal Server Error\n");
            builder.append("Content-Type: text/html; charset=utf-8\n\n");
            builder.append("GitHub request failed: " + e.getMessage());
          }

        }else if (request.contains("weather?")) {
          Map<String, String> query_pairs = new LinkedHashMap<String, String>();
          try {
            query_pairs = splitQuery(request.replace("weather?", ""));
            String city = query_pairs.get("city");
            String state = query_pairs.get("state");

            if (city == null || state == null) {
              builder.append("HTTP/1.1 400 Bad Request\n");
              builder.append("Content-Type: text/html; charset=utf-8\n\n");
              builder.append("Missing parameters. Usage: /weather?city=Phoenix&state=AZ");
            } else {
              builder.append("HTTP/1.1 200 OK\n");
              builder.append("Content-Type: text/html; charset=utf-8\n\n");
              builder.append("Pretending to fetch weather for: " + city + ", " + state);
            }
          } catch (Exception e) {
            builder.append("HTTP/1.1 500 Internal Server Error\n");
            builder.append("Content-Type: text/html; charset=utf-8\n\n");
            builder.append("Error: " + e.getMessage());
          }
        } else if (request.contains("greet?")) {
          Map<String, String> query_pairs = new LinkedHashMap<String, String>();
          try {
            query_pairs = splitQuery(request.replace("greet?", ""));
            String name = query_pairs.get("name");
            String lang = query_pairs.get("lang");

            if (name == null || lang == null) {
              builder.append("HTTP/1.1 400 Bad Request\n");
              builder.append("Content-Type: text/html; charset=utf-8\n\n");
              builder.append("Missing parameters. Usage: /greet?name=Dani&lang=es");
            } else {
              String greeting;
              switch (lang.toLowerCase()) {
                case "es": greeting = "Hola"; break;
                case "fr": greeting = "Bonjour"; break;
                case "de": greeting = "Hallo"; break;
                default: greeting = "Hello";
              }
              builder.append("HTTP/1.1 200 OK\n");
              builder.append("Content-Type: text/html; charset=utf-8\n\n");
              builder.append(greeting + ", " + name + "!");
            }
          } catch (Exception e) {
            builder.append("HTTP/1.1 500 Internal Server Error\n");
            builder.append("Content-Type: text/html; charset=utf-8\n\n");
            builder.append("Error: " + e.getMessage());
          }
        } else {
          builder.append("HTTP/1.1 400 Bad Request\n");
          builder.append("Content-Type: text/html; charset=utf-8\n\n");
          builder.append("I am not sure what you want me to do...");
        }

        response = builder.toString().getBytes();
      }
    } catch (IOException e) {
      e.printStackTrace();
      response = ("<html>ERROR: " + e.getMessage() + "</html>").getBytes();
    }

    return response;
  }

  public static Map<String, String> splitQuery(String query) throws UnsupportedEncodingException {
    Map<String, String> query_pairs = new LinkedHashMap<>();
    String[] pairs = query.split("&");
    for (String pair : pairs) {
      int idx = pair.indexOf("=");
      query_pairs.put(URLDecoder.decode(pair.substring(0, idx), "UTF-8"),
              URLDecoder.decode(pair.substring(idx + 1), "UTF-8"));
    }
    return query_pairs;
  }

  public static String buildFileList() {
    ArrayList<String> filenames = new ArrayList<>();
    File directoryPath = new File("www/");
    filenames.addAll(Arrays.asList(directoryPath.list()));
    if (filenames.size() > 0) {
      StringBuilder builder = new StringBuilder();
      builder.append("<ul>\n");
      for (var filename : filenames) {
        builder.append("<li>" + filename + "</li>");
      }
      builder.append("</ul>\n");
      return builder.toString();
    } else {
      return "No files in directory";
    }
  }

  public static byte[] readFileInBytes(File f) throws IOException {
    FileInputStream file = new FileInputStream(f);
    ByteArrayOutputStream data = new ByteArrayOutputStream(file.available());
    byte buffer[] = new byte[512];
    int numRead = file.read(buffer);
    while (numRead > 0) {
      data.write(buffer, 0, numRead);
      numRead = file.read(buffer);
    }
    file.close();
    byte[] result = data.toByteArray();
    data.close();
    return result;
  }

  public String fetchURL(String aUrl) {
    StringBuilder sb = new StringBuilder();
    URLConnection conn = null;
    InputStreamReader in = null;
    try {
      URL url = new URL(aUrl);
      conn = url.openConnection();
      if (conn != null)
        conn.setReadTimeout(20 * 1000);
      if (conn != null && conn.getInputStream() != null) {
        in = new InputStreamReader(conn.getInputStream(), Charset.defaultCharset());
        BufferedReader br = new BufferedReader(in);
        if (br != null) {
          int ch;
          while ((ch = br.read()) != -1) {
            sb.append((char) ch);
          }
          br.close();
        }
      }
      in.close();
    } catch (Exception ex) {
      System.out.println("Exception in url request:" + ex.getMessage());
    }
    return sb.toString();
  }
}
