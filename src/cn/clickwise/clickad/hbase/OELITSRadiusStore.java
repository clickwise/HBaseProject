package cn.clickwise.clickad.hbase;

import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.HColumnDescriptor;
import org.apache.hadoop.hbase.HTableDescriptor;
import org.apache.hadoop.hbase.KeyValue;
import org.apache.hadoop.hbase.MasterNotRunningException;
import org.apache.hadoop.hbase.ZooKeeperConnectionException;
import org.apache.hadoop.hbase.client.HBaseAdmin;
import org.apache.hadoop.hbase.client.HTable;
import org.apache.hadoop.hbase.client.HTablePool;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.client.Scan;

import cn.clickwise.lib.code.MD5Code;
import cn.clickwise.lib.string.SSO;
import cn.clickwise.lib.time.TimeOpera;

/**
 * 根据md5(ip)+time+status查询 rowkey: IP+时间+状态 cf:column 有两个，分别为{ rid:c(value 为旧帐号)
 * oip:c (value 为 旧ip)}
 * 
 * @author zkyz
 */
public class OELITSRadiusStore extends RadiusStore {

	// 连接hadoop平台的配置
	public static Configuration configuration;
	public static HTablePool pool;
	public static String RID = "rid";
	public static String OIP = "oip";
	public static String TNAME = "htradius";

	public String default_day = "";

	static {

		configuration = HBaseConfiguration.create();

		/************
		 * hn *****************
		 * configuration.set("hbase.zookeeper.property.clientPort", "2181");
		 * configuration.set("hbase.zookeeper.quorum", "192.168.10.103");
		 * configuration.set("hbase.master", "192.168.10.103:60000");
		 ********************************/

		/************
		 * local*******************
		 * configuration.set("hbase.zookeeper.property.clientPort", "2181");
		 * configuration.set("hbase.zookeeper.quorum", "192.168.110.80");
		 * configuration.set("hbase.master", "192.168.110.80:60000");
		 ************************************/

		/************ zj *****************/
		configuration.set("hbase.zookeeper.property.clientPort", "2181");
		configuration.set("hbase.zookeeper.quorum", "192.168.10.130");
		configuration.set("hbase.master", "192.168.10.128:60010");
		/********************************/

		pool = new HTablePool(configuration, 100);
		String[] cfs = { RID, OIP };
		createTable(TNAME, cfs);

	}

	/**
	 * 表不存在才创建
	 * 
	 * @param tableName
	 */
	public static void createTable(String tableName, String[] cfs) {

		System.out.println("start create table ......");
		try {
			HBaseAdmin hBaseAdmin = new HBaseAdmin(configuration);
			if (hBaseAdmin.tableExists(tableName)) {// 如果存在要创建的表，返回
				// hBaseAdmin.disableTable(tableName);
				// hBaseAdmin.deleteTable(tableName);
				System.out.println(tableName + " is exist");
				return;
			}
			HTableDescriptor tableDescriptor = new HTableDescriptor(tableName);
			for (int j = 0; j < cfs.length; j++) {
				tableDescriptor.addFamily(new HColumnDescriptor(cfs[j]));
			}
			hBaseAdmin.createTable(tableDescriptor);

		} catch (MasterNotRunningException e) {
			e.printStackTrace();
		} catch (ZooKeeperConnectionException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		System.out.println("end create table ......");

	}

	@Override
	public void write(String record) {

		String ip = "";
		String status = "";
		String radiusid = "";
		long time = 0;
		String time_str = "";

		String[] fields = record.split("\\s+");
		if (fields.length < 1) {
			return;
		}

		ip = fields[0];

		String md5ip = MD5Code.makeMD5(ip);

		for (int j = 1; j < fields.length; j++) {

			String[] tokens = null;
			tokens = fields[j].split(":");
			if (tokens.length != 3) {
				continue;
			}

			time = Long.parseLong(tokens[0]);
			radiusid = tokens[1];
			status = tokens[2];
			time_str = TimeOpera.long2strm(time);

			String rowkey = md5ip + time_str.replaceAll("\\s+", "") + status;
			Put put = new Put(rowkey.getBytes());
			put.add(RID.getBytes(), "c".getBytes(), radiusid.getBytes());
			put.add(OIP.getBytes(), "c".getBytes(), ip.getBytes());

			try {
				pool.getTable(TNAME).put(put);
				System.err.println("add " + rowkey);
				pool.closeTablePool(TNAME);
				Thread.sleep(2);
			} catch (Exception e) {
				e.printStackTrace();
			}

		}
	}

	public Put getPut(String record) {

		String ip = "";
		String status = "";
		String radiusid = "";
		long time = 0;
		String time_str = "";

		String[] fields = record.split("\\s+");
		if (fields.length < 1) {
			return null;
		}

		ip = fields[0];

		String md5ip = MD5Code.makeMD5(ip);
		Put put=null;
		
		for (int j = 1; j < fields.length; j++) {

			String[] tokens = null;
			tokens = fields[j].split(":");
			if (tokens.length != 3) {
				continue;
			}

			time = Long.parseLong(tokens[0]);
			radiusid = tokens[1];
			status = tokens[2];
			time_str = TimeOpera.long2strm(time);

			String rowkey = md5ip + time_str.replaceAll("\\s+", "") + status;
			put = new Put(rowkey.getBytes());
			put.add(RID.getBytes(), "c".getBytes(), radiusid.getBytes());
			put.add(OIP.getBytes(), "c".getBytes(), ip.getBytes());

		}
		
		return put;
	}
	
	/**
	 * 写入hbase数据库，批量写入
	 * 
	 * @param puts
	 *            待写入hbase的Put列表
	 * @param wal
	 *            是否开启Write Ahead Log，默认开启，需要手动关闭
	 * @param autoFlush
	 *            Autoflush 默认关闭，需要手动开启
	 * @param writeBuffer
	 *            Write Buffer Size 默认值2M
	 */
	public void writeBat(List<Put> puts, boolean wal, boolean autoFlush,
			long writeBuffer) {

		try {

			pool.getTable(TNAME).setAutoFlush(autoFlush);
			if (writeBuffer != 0) {

				pool.getTable(TNAME).setWriteBufferSize(writeBuffer);
			}

			long all = System.currentTimeMillis();

			System.out.println("start time = " + all);
			int count = 10000;
			byte[] buffer = new byte[128];
			Random r = new Random();

			pool.getTable(TNAME).put(puts);

			System.out.println("WAL=" + wal + ",autoFlush=" + autoFlush
					+ ",buffer=" + writeBuffer + ",count=" + count);
			long end = System.currentTimeMillis();
			System.out.println("total need time = " + (end - all) * 1.0 / 1000
					+ "s");

			System.out.println("insert complete" + ",costs:"
					+ (System.currentTimeMillis() - all) * 1.0 / 1000 + "ms");
			
		} catch (Exception e) {
             System.err.println(e.getMessage());
		}

	}

	public void write_default_day(String record) {

		String ip = "";
		String status = "";
		String radiusid = "";
		String time = "";

		String default_time = default_day + " 12:29:20";

		String[] fields = record.split("\\s+");
		if (fields.length < 1) {
			return;
		}

		ip = fields[0];

		String md5ip = MD5Code.makeMD5(ip);

		for (int j = 1; j < fields.length; j++) {

			String[] tokens = null;
			tokens = fields[j].split(":");
			if (tokens.length != 3) {
				continue;
			}

			time = tokens[0];
			radiusid = tokens[1];
			status = tokens[2];

			String rowkey = md5ip + default_time.replaceAll("\\s+", "")
					+ status;
			Put put = new Put(rowkey.getBytes());
			put.add(RID.getBytes(), "c".getBytes(), radiusid.getBytes());
			put.add(OIP.getBytes(), "c".getBytes(), ip.getBytes());

			try {
				pool.getTable(TNAME).put(put);
				System.err.println("add " + rowkey);
				pool.closeTablePool(TNAME);
				Thread.sleep(10);
			} catch (Exception e) {
				e.printStackTrace();
			}

		}
	}

	@Override
	public List<String> get(String ip, String time) {

		String md5ip = MD5Code.makeMD5(ip);

		System.out.println("ip:" + ip + " time:" + time);
		String startkey = md5ip
				+ TimeOpera.getOnedayBefore(time).replaceAll("\\s+", "") + "0";
		String endkey = md5ip
				+ TimeOpera.getOnedayAfter(time).replaceAll("\\s+", "") + "1";

		List<String> rlist = new ArrayList<String>();
		try {
			Scan s = new Scan(startkey.getBytes(), endkey.getBytes());

			ResultScanner rs = pool.getTable(TNAME).getScanner(s);

			String cf = "";
			String va = "";

			for (Result r : rs) {
				System.out.println("获得到rowkey:" + new String(r.getRow()));
				for (KeyValue keyValue : r.raw()) {
					cf = new String(keyValue.getFamily());
					va = new String(keyValue.getValue());
					if (cf.equals("rid")) {
						rlist.add(va);
					}

					System.out.println("列：" + cf + "====值:" + va);
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

		return rlist;
	}

	@Override
	public List<String> get(String ip) {
		// TODO Auto-generated method stub
		return null;
	}

	public static void main(String[] args) {

		if (args.length < 1) {
			System.err.println("Usage:<get or add> [<IP> <date> <time>|<day>]");
			System.exit(1);
		}

		PrintWriter countPW = null;
		String ga = args[0];

		String day = "";

		String ip = "";
		String date = "";
		String time = "";
		int threadnum = 0;

		try {
			countPW = new PrintWriter(new FileWriter("countlog.txt"));
		} catch (Exception e) {
			e.printStackTrace();
		}

		if (args.length == 2) {
			day = args[1];
			if (day.indexOf("-") < 0) {
				day = TimeOpera.int2string(day);
			}
			System.err.println("day:" + day);
		}

		if (args.length == 4) {
			ip = args[1];
			date = args[2];
			time = args[3];
		}

		OELITSRadiusStore eitsl = new OELITSRadiusStore();

		if (ga.equals("add")) {

			eitsl.default_day = day;

			InputStreamReader isr = new InputStreamReader(System.in);
			BufferedReader br = new BufferedReader(isr);

			String line = "";
			int count = 0;
			List<Put> puts=new ArrayList<Put>();
			Put onePut=null;
			try {
				while ((line = br.readLine()) != null) {
					try {
						count++;
						if (count % 1000 == 0) {
							countPW.println(count);
							countPW.flush();
							puts=new ArrayList<Put>();
							eitsl.writeBat(puts, false, false, 1024*1024*64);
						}
						// Thread.sleep(200);
						if (SSO.tioe(line)) {
							continue;
						}
						onePut=eitsl.getPut(line);
						if(onePut==null)
						{
							continue;
						}
						
						puts.add(onePut);

					} catch (Exception e) {
						System.err.println(e.getMessage());
						System.out.println("count:" + count);
						countPW.close();
					}

				}
				System.out.println("count:" + count);
				countPW.close();
			} catch (Exception e) {
				System.err.println(e.getMessage());
				System.out.println("count:" + count);
				countPW.close();
			}
		} else if (ga.equals("get")) {
			List<String> rs = eitsl.get(ip, date + " " + time);
			for (int j = 0; j < rs.size(); j++) {
				System.out.println(rs.get(j));
			}
		}

	}
}