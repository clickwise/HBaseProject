package cn.clickwise.clickad.hbase;

import java.text.SimpleDateFormat;  
import java.util.ArrayList;
import java.util.Date;  
import java.util.List;
  
import org.apache.hadoop.conf.Configuration;  
import org.apache.hadoop.hbase.client.Put;  
import org.apache.hadoop.hbase.mapreduce.TableOutputFormat;  
import org.apache.hadoop.hbase.mapreduce.TableReducer;  
import org.apache.hadoop.hbase.util.Bytes;  
import org.apache.hadoop.io.LongWritable;  
import org.apache.hadoop.io.NullWritable;  
import org.apache.hadoop.io.Text;  
import org.apache.hadoop.mapreduce.Counter;  
import org.apache.hadoop.mapreduce.Job;  
import org.apache.hadoop.mapreduce.Mapper;  
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;  
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat;  

import cn.clickwise.lib.code.MD5Code;
import cn.clickwise.lib.time.TimeOpera;

public class HbaseBatchImport {

	static class BatchImportMapper extends
			Mapper<Object, Text,Text, Text> {
		
		SimpleDateFormat dateformat1 = new SimpleDateFormat("yyyyMMddHHmmss");
		
		Text k2=new Text();
		Text v2 = new Text();

		protected void map(Object key, Text value, Context context)
				throws java.io.IOException, InterruptedException {
			
			try {

				String key_str=RandomGen.getRandomStr();
				k2.set(key_str);
				v2.set(value.toString());
				context.write(k2, v2);
			} catch (NumberFormatException e) {
				final Counter counter = context.getCounter("BatchImport",
						"ErrorFormat");
				counter.increment(1L);
				
			}
		};
	}

	static class BatchImportReducer extends
			TableReducer<Text, Text, NullWritable> {
		
		public static String RID = "rid";
		public static String OIP = "oip";
		public static String TNAME = "hradius";
		
		protected void reduce(Text key,
				java.lang.Iterable<Text> values, Context context)
				throws java.io.IOException, InterruptedException {
			
			for (Text text : values) {
				List<Put> puts=getPut(text.toString());
				

				// 省略其他字段，调用put.add(....)即可
				for(int j=0;j<puts.size();j++)
				{
				  context.write(NullWritable.get(), puts.get(j));
				}
			}
		};
		
		public List<Put> getPut(String record)
		{
			String ip = "";
			String status = "";
			String radiusid = "";
			long time = 0;
			String time_str="";
			
			String[] fields = record.split("\\s+");
			if (fields.length < 1) {
				return null;
			}

			ip = fields[0];

			String md5ip = MD5Code.makeMD5(ip);

			List<Put> list=new ArrayList<Put>();
			for (int j = 1; j < fields.length; j++) {
				
			    String[] tokens=null;
			    tokens=fields[j].split(":");
			    if(tokens.length!=3)
			    {
			    	continue;
			    }
			    
			    time=Long.parseLong(tokens[0]);
			    radiusid=tokens[1];
			    status=tokens[2];	    
			    time_str=TimeOpera.long2strm(time);
			   		    		 		
				String rowkey = md5ip + time_str.replaceAll("\\s+", "") + status;
				Put put = new Put(rowkey.getBytes());
				put.add(RID.getBytes(), "c".getBytes(), radiusid.getBytes());
				put.add(OIP.getBytes(), "c".getBytes(), ip.getBytes());
				list.add(put);
		    }
			
			return list;
	   }
	}
	public static void main(String[] args) throws Exception {
		
		final Configuration configuration = new Configuration();
		// 设置zookeeper
		configuration.set("hbase.zookeeper.quorum", "192.168.10.130");

		// 设置hbase表名称
		configuration.set(TableOutputFormat.OUTPUT_TABLE, "htradius");

		// 将该值改大，防止hbase超时退出
		configuration.set("dfs.socket.timeout", "180000");

		final Job job = new Job(configuration, "HBaseBatchImport");

		job.setMapperClass(BatchImportMapper.class);
		job.setReducerClass(BatchImportReducer.class);
		// 设置map的输出，不设置reduce的输出类型
		job.setMapOutputKeyClass(LongWritable.class);
		job.setMapOutputValueClass(Text.class);

		job.setInputFormatClass(TextInputFormat.class);
		// 不再设置输出路径，而是设置输出格式类型
		job.setOutputFormatClass(TableOutputFormat.class);

		FileInputFormat.setInputPaths(job, "hdfs://192.168.10.107:8020/user/clickwise/hradiusInput");

		job.waitForCompletion(true);
	}

}
