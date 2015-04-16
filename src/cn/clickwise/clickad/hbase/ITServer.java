package cn.clickwise.clickad.hbase;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cn.clickwise.lib.string.SSO;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;


/**
 * 根据ip和时间查询radiusId
 * @author zkyz
 */
public class ITServer implements Runnable{
	
	private Properties properties = new Properties();
	static Logger logger = LoggerFactory.getLogger(ITServer.class);
	
	ELITSRadiusStore elits=new ELITSRadiusStore();
	
	@Override
	public void run() {
		// TODO Auto-generated method stub
		// 配置成根据传入请求的前缀不同调用不同的处理程序
		// 每种请求对应一个handler
		
		try {
			HttpServer hs = HttpServer.create(new InetSocketAddress(Integer.parseInt(properties.getProperty("port"))), 0);

			ITHandler test_handler = new ITHandler();
			hs.createContext("/ipq", test_handler);

			hs.setExecutor(null);
			hs.start();

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	
	private class ITHandler implements HttpHandler{
		
		@Override
		public void handle(HttpExchange exchange) throws IOException {
			
			String uri = exchange.getRequestURI().toString();
			System.out.println("uri:"+uri);
			uri = uri.replaceFirst("\\/ipq\\?", "");
			HashMap<String,String> phash=convertParams(uri);
			String ip="";
			String time="";
			ip=phash.get("ip");
			time=phash.get("time");
			
			//只有日期
			if(time.length()<15)
			{
			   time=time.trim()+"_12:29:20";
			}
			
			List<String> oradiusIds=elits.get(ip, time.replaceAll("_", " "));
			List<String> radiusIds=redupList(oradiusIds);
			String restr="";
			if(radiusIds==null)
			{
				restr="nothing";
			}
			else
			{
			  for(int i=0;i<radiusIds.size()-1;i++)
			  {
				 restr=restr+radiusIds.get(i)+",";
			  }
			  restr=restr+radiusIds.get(radiusIds.size()-1);
			}
			
			System.err.println("restr:"+restr);
			//encode_res = encode_res.replaceAll("\\s+", "");	
			//exchange.sendResponseHeaders(200, encode_res.length());
			exchange.sendResponseHeaders(200,0);
			OutputStream os = exchange.getResponseBody();
			OutputStreamWriter osw=new OutputStreamWriter(os,"gbk");
			PrintWriter pw=new PrintWriter(osw);
			//os.write(encode_res.getBytes());
			pw.println(restr);
			pw.flush();
			pw.close();
			os.close();
			
		}
		
		public List<String> redupList(List<String> list)
		{
			List<String> rlist=new ArrayList<String>();
			HashMap<String,Integer> rhash=new HashMap<String,Integer>();
			String radiusId="";
			for(int i=0;i<list.size();i++)
			{
				radiusId=list.get(i);
				if(SSO.tioe(radiusId))
				{
					continue;
				}
				
				if(!(rhash.containsKey(radiusId)))
				{
				   rhash.put(radiusId, 1);	
				}	
			}
			
			
			for(Map.Entry<String, Integer> e:rhash.entrySet())
			{
				rlist.add(e.getKey());
			}
			
			
			return rlist;
		}
	}
	
	public HashMap<String,String> convertParams(String param_str)
	{
		String[] fields=param_str.split("&");
		if(fields==null||fields.length<1)
		{
			return null;
		}
		
		HashMap<String,String> phash=new HashMap<String,String>();
		String key="";
		String value="";
		
		for(int i=0;i<fields.length;i++)
		{
		  key=SSO.beforeStr(fields[i], "=");
		  value=SSO.afterStr(fields[i], "=");
		  if(SSO.tioe(key)||SSO.tioe(value))
		  {
			  continue;
		  }
		  
		  phash.put(key, value);
		}
		
		return phash;
	}
	
	
	public void read_input_parameters(String[] args) {
		int i;
		for (i = 0; (i < args.length) && ((args[i].charAt(0)) == '-'); i++) {
			switch ((args[i].charAt(1))) {
			case 'h':
				print_help();
				System.exit(0);
			case 'p':
				i++;
				properties.setProperty("port", args[i]);
				break;			
			default:
				System.out.println("Unrecognized option " + args[i] + "!");
				print_help();
				System.exit(0);
			}
		}

		System.out.println(properties.toString());
	}
	
	public static void print_help() {
		System.out.println("usage: ITServer [options]");
		System.out.println("options: -h  -> this help");
		System.out.println("         -p  server port");
	}
	
	public static void main(String[] args)
	{
		ITServer cps=new ITServer();
		cps.read_input_parameters(args);
		Thread serverThread = new Thread(cps);
		serverThread.start();	
	}
	
}
