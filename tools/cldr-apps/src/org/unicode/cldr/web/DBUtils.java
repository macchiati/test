/*
 * Copyright (C) 2004-2012 IBM Corporation and Others. All Rights Reserved.
 */
package org.unicode.cldr.web;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.unicode.cldr.util.CLDRConfig;
import org.unicode.cldr.util.CLDRLocale;
import org.unicode.cldr.util.StackTracker;

import com.ibm.icu.text.UnicodeSet;

//import org.apache.tomcat.dbcp.dbcp.BasicDataSource;

/**
 * All of the database related stuff has been moved here.
 * 
 * @author srl
 *
 */
public class DBUtils {
	private static final boolean DEBUG=false;//CldrUtility.getProperty("TEST", false);
	private static final boolean DEBUG_QUICKLY=false;//CldrUtility.getProperty("TEST", false);

	
	private static DBUtils instance = null;
	private static final String JDBC_SURVEYTOOL = ("jdbc/SurveyTool");
	private static DataSource datasource = null;
	// DB stuff
	public static String db_driver = null;
	public static String db_protocol = null;
	public static String CLDR_DB_U = null;
	public static String CLDR_DB_P = null;
	public static String cldrdb_u = null;
	public static String CLDR_DB;
//	public static String cldrdb = null;
	public static String CLDR_DB_CREATESUFFIX = null;
	public static String CLDR_DB_SHUTDOWNSUFFIX = null;
	public static boolean db_Derby = false;
	public static boolean db_Mysql = false;
	// === DB workarounds :( - derby by default
	public static String DB_SQL_IDENTITY = "GENERATED ALWAYS AS IDENTITY";
	public static String DB_SQL_VARCHARXPATH = "varchar(1024)";
	public static String DB_SQL_WITHDEFAULT = "WITH DEFAULT";
	public static String DB_SQL_TIMESTAMP0 = "TIMESTAMP";
	public static String DB_SQL_CURRENT_TIMESTAMP0 = "CURRENT_TIMESTAMP";
	public static String DB_SQL_MIDTEXT = "VARCHAR(1024)";
	public static String DB_SQL_BIGTEXT = "VARCHAR(16384)";
	public static String DB_SQL_UNICODE = "VARCHAR(16384)"; // unicode type string
    public static  String DB_SQL_LAST_MOD =  " last_mod TIMESTAMP NOT NULL WITH DEFAULT CURRENT_TIMESTAMP  ";
	public static String DB_SQL_ALLTABLES = "select tablename from SYS.SYSTABLES where tabletype='T'";
	public static String DB_SQL_BINCOLLATE = "";
	public static String DB_SQL_BINTRODUCER = "";
	public static int db_number_open = 0;
	public static int db_number_used = 0;
	private static int db_UnicodeType =             java.sql.Types.VARCHAR; /* for setNull  - see java.sql.Types */
	private static final StackTracker tracker = DEBUG?new StackTracker():null; // new StackTracker(); - enable, to track unclosed connections
	
	public Appendable stats(Appendable output) throws IOException {
		return output.append("DBUtils: currently open: "+db_number_open)
		.append(", max open: " + db_max_open)
		.append(", total used: " + db_number_used);
	}
	public Appendable statsShort(Appendable output) throws IOException {
		return output.append(""+db_number_open)
		.append("/" + db_max_open);
	}
	
	public static void closeDBConnection(Connection conn) {
		if (conn != null) {
		    if(SurveyMain.isUnofficial() && tracker!=null) {
		        tracker.remove(conn);
		    }
			try {
				conn.close();
			} catch (SQLException e) {
				System.err.println(DBUtils.unchainSqlException(e));
				e.printStackTrace();
			}
			db_number_open--;
		}
	}
	public static final String escapeBasic(byte what[]) {
		return escapeLiterals(what);
	}

	public static final String escapeForMysql(byte what[]) {
		boolean hasEscapeable = false;
		boolean hasNonEscapeable = false;
		for (byte b : what) {
			int j = ((int) b) & 0xff;
			char c = (char) j;
			if (escapeIsBasic(c)) {
				continue;
			} else if (escapeIsEscapeable(c)) {
				hasEscapeable = true;
			} else {
				hasNonEscapeable = true;
			}
		}
		if (hasNonEscapeable) {
			return escapeHex(what);
		} else if (hasEscapeable) {
			return escapeLiterals(what);
		} else {
			return escapeBasic(what);
		}
	}

	public static String escapeForMysql(String what)
			throws UnsupportedEncodingException {
		if (what == null) {
			return "NULL";
		} else if (what.length() == 0) {
			return "\"\"";
		} else {
			return escapeForMysql(what.getBytes("ASCII"));
		}
	}

	public static String escapeForMysqlUtf8(String what)
			throws UnsupportedEncodingException {
		if (what == null) {
			return "NULL";
		} else if (what.length() == 0) {
			return "\"\"";
		} else {
			return escapeForMysql(what.getBytes("UTF-8"));
		}
	}

	public static final String escapeHex(byte what[]) {
		StringBuffer out = new StringBuffer("x'");
		for (byte b : what) {
			int j = ((int) b) & 0xff;
			if (j < 0x10) {
				out.append('0');
			}
			out.append(Integer.toHexString(j));
		}
		out.append("'");
		return out.toString();
	}

	public static final boolean escapeIsBasic(char c) {
		return ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
				|| (c >= '0' && c <= '9') || (c == ' ' || c == '.' || c == '/'
				|| c == '[' || c == ']' || c == '=' || c == '@' || c == '_'
				|| c == ',' || c == '&' || c == '-' || c == '(' || c == ')'
				|| c == '#' || c == '$' || c == '!'));
	}

	public static final boolean escapeIsEscapeable(char c) {
		return (c == 0 || c == '\'' || c == '"' || c == '\b' || c == '\n'
				|| c == '\r' || c == '\t' || c == 26 || c == '\\');
	}

	public static final String escapeLiterals(byte what[]) {
		StringBuffer out = new StringBuffer("'");
		for (byte b : what) {
			int j = ((int) b) & 0xff;
			char c = (char) j;
			switch (c) {
			case 0:
				out.append("\\0");
				break;
			case '\'':
				out.append("'");
				break;
			case '"':
				out.append("\\");
				break;
			case '\b':
				out.append("\\b");
				break;
			case '\n':
				out.append("\\n");
				break;
			case '\r':
				out.append("\\r");
				break;
			case '\t':
				out.append("\\t");
				break;
			case 26:
				out.append("\\z");
				break;
			case '\\':
				out.append("\\\\");
				break;
			default:
				out.append(c);
			}
		}
		out.append("'");
		return out.toString();
	}

	public static DBUtils peekInstance() {
		return instance;
	}
	public synchronized static DBUtils getInstance() {
		if (instance == null) {
			instance = new DBUtils();
		}
		return instance;
	}

	public synchronized static void makeInstanceFrom(DataSource dataSource2) {
		if(instance==null) {
			instance = new DBUtils(dataSource2);
		} else {
			throw new IllegalArgumentException("Already initted.");
		}
	}
	// fix the UTF-8 fail
	public static final String getStringUTF8(ResultSet rs, int which)
			throws SQLException {
		if (db_Derby) { // unicode
			String str =  rs.getString(which);
			if(rs.wasNull()) return null;
			return str;
		}
		byte rv[] = rs.getBytes(which);
		if(rs.wasNull()) return null;
		if (rv != null) {
			String unicode;
			try {
				unicode = new String(rv, "UTF-8");
			} catch (UnsupportedEncodingException e) {
				e.printStackTrace();
				throw new InternalError(e.toString());
			}
			return unicode;
		} else {
			return null;
		}
	}

	public static boolean hasTable(Connection conn, String table) {
		String canonName = db_Derby ? table.toUpperCase() : table;
		try {
			ResultSet rs;

			if (db_Derby) {
				DatabaseMetaData dbmd = conn.getMetaData();
				rs = dbmd.getTables(null, null, canonName, null);
			} else {
				Statement s = conn.createStatement();
				rs = s.executeQuery("show tables like '" + canonName + "'");
			}

			if (rs.next() == true) {
				rs.close();
				// System.err.println("table " + canonName + " did exist.");
				return true;
			} else {
				SurveyLog.debug("table " + canonName + " did not exist.");
				return false;
			}
		} catch (SQLException se) {
			SurveyMain.busted("While looking for table '" + table + "': ", se);
			return false; // NOTREACHED
		}
	}

	public static final void setStringUTF8(PreparedStatement s, int which,
			String what) throws SQLException {
		if(what==null) {
			s.setNull(which,db_UnicodeType);
		}
		if (db_Derby) {
			s.setString(which, what);
		} else {
			byte u8[];
			if (what == null) {
				u8 = null;
			} else {
				try {
					u8 = what.getBytes("UTF-8");
				} catch (UnsupportedEncodingException e) {
					e.printStackTrace();
					throw new InternalError(e.toString());
				}
			}
			s.setBytes(which, u8);
		}
	}

	static int sqlCount(WebContext ctx, Connection conn, PreparedStatement ps) {
		int rv = -1;
		try {
			ResultSet rs = ps.executeQuery();
			if (rs.next()) {
				rv = rs.getInt(1);
			}
			rs.close();
		} catch (SQLException se) {
			String complaint = " Couldn't query count - "
					+ unchainSqlException(se) + " -  ps";
			System.err.println(complaint);
			ctx.println("<hr><font color='red'>ERR: " + complaint
					+ "</font><hr>");
		}
		return rv;
	}

	static int sqlCount(WebContext ctx, Connection conn, String sql) {
		int rv = -1;
		try {
			Statement s = conn.createStatement();
			ResultSet rs = s.executeQuery(sql);
			if (rs.next()) {
				rv = rs.getInt(1);
			}
			rs.close();
			s.close();
		} catch (SQLException se) {
			String complaint = " Couldn't query count - "
					+ unchainSqlException(se) + " - " + sql;
			System.err.println(complaint);
			ctx.println("<hr><font color='red'>ERR: " + complaint
					+ "</font><hr>");
		}
		return rv;
	}
	
	public static String[] sqlQueryArray(Connection conn, String str) throws SQLException {
		return sqlQueryArrayArray(conn,str)[0];
	}
	
	public static String[][] sqlQueryArrayArray(Connection conn, String str) throws SQLException {
		Statement s  = null;
		ResultSet rs  = null;
		try {
			s = conn.createStatement();
			rs = s.executeQuery(str);
			ArrayList<String[]> al = new ArrayList<String[]>();
			while(rs.next()) {
				al.add(arrayOfResult(rs));
			}
			return al.toArray(new String[al.size()][]);
		} finally {
			if(rs!=null) {
				rs.close();
			}
			if(s!=null) {
				s.close();
			}
		}
	}
//
//	private String[] arrayOfResult(ResultSet rs) throws SQLException {
//		ResultSetMetaData rsm = rs.getMetaData();
//		String ret[] = new String[rsm.getColumnCount()];
//		for(int i=0;i<ret.length;i++) {
//			ret[i]=rs.getString(i+1);
//		}
//		return ret;
//	}
	public  static String sqlQuery(Connection conn, String str) throws SQLException {
		return sqlQueryArray(conn,str)[0];
	}
	

	public static int sqlUpdate(WebContext ctx, Connection conn, PreparedStatement ps) {
		int rv = -1;
		try {
			rv = ps.executeUpdate();
		} catch (SQLException se) {
			String complaint = " Couldn't sqlUpdate  - "
					+ unchainSqlException(se) + " -  ps";
			System.err.println(complaint);
			ctx.println("<hr><font color='red'>ERR: " + complaint
					+ "</font><hr>");
		}
		return rv;
	}

	public static final String unchainSqlException(SQLException e) {
		String echain = "SQL exception: \n ";
		SQLException laste = null;
		while (e != null) {
			laste = e;
			echain = echain + " -\n " + e.toString();
			e = e.getNextException();
		}
		String stackStr = "\n unknown Stack";
		try {
			StringWriter asString = new StringWriter();
			laste.printStackTrace(new PrintWriter(asString));
			stackStr = "\n Stack: \n " + asString.toString();
		} catch (Throwable tt) {
			stackStr = "\n unknown stack (" + tt.toString() + ")";
		}
		return echain + stackStr;
	}

	File dbDir = null;

	// File dbDir_u = null;
	static String dbInfo = null;
	
	public boolean isBogus() {
		return (datasource==null);
	}

	private DBUtils() {
		// Initialize DB context
		try {
			Context initialContext = new InitialContext();
			datasource = (DataSource) initialContext.lookup("java:comp/env/" + JDBC_SURVEYTOOL);
			//datasource = (DataSource) envContext.lookup("ASDSDASDASDASD");
			
			if(datasource!=null) {
				System.err.println("Got datasource: " + datasource.toString());
			}
			Connection c = null;
			try {
				if(datasource!=null) {
					c = datasource.getConnection();
					DatabaseMetaData dmd = c.getMetaData();
					dbInfo = dmd.getDatabaseProductName()+" v"+dmd.getDatabaseProductVersion();
					setupSqlForServerType();
					SurveyLog.debug("Metadata: "+ dbInfo);
				}
			} catch (SQLException  t) {
                datasource = null;
				throw new IllegalArgumentException(getClass().getName()+": WARNING: we require a JNDI datasource.  "
								+ "'"+JDBC_SURVEYTOOL+"'"
								+ ".getConnection() returns : "
								+ t.toString()+"\n"+unchainSqlException(t));
			} finally {
				if (c != null)
					try {
						c.close();
					} catch (Throwable tt) {
						System.err.println("Couldn't close datasource's conn: "
								+ tt.toString());
						tt.printStackTrace();
					}
			}
		} catch (NamingException nc) {
			nc.printStackTrace();
			datasource = null;
			throw new Error("Couldn't load context " + JDBC_SURVEYTOOL
					+ " - not using datasource.",nc);
		}
		
	}

	public DBUtils(DataSource dataSource2) {
		datasource=dataSource2;
		Connection c = null;
		try {
			if(datasource!=null) {
				c = datasource.getConnection();
				DatabaseMetaData dmd = c.getMetaData();
				dbInfo = dmd.getDatabaseProductName()+" v"+dmd.getDatabaseProductVersion();
				setupSqlForServerType();
				if(db_Derby) {
					c.setAutoCommit(false);
				}
				boolean autoCommit = c.getAutoCommit();
				if(autoCommit==true) {
					throw new IllegalArgumentException("autoCommit was true, expected false. Check your configuration.");
				}
				SurveyLog.debug("Metadata: "+ dbInfo + ", autocommit: " + autoCommit);
			}
		} catch (SQLException  t) {
            datasource = null;
			throw new IllegalArgumentException(getClass().getName()+": WARNING: we require a JNDI datasource.  "
							+ "'"+JDBC_SURVEYTOOL+"'"
							+ ".getConnection() returns : "
							+ t.toString()+"\n"+unchainSqlException(t));
		} finally {
			if (c != null)
				try {
					c.close();
				} catch (Throwable tt) {
					System.err.println("Couldn't close datasource's conn: "
							+ tt.toString());
					tt.printStackTrace();
				}
		}
	}
	private void setupSqlForServerType() {
		SurveyLog.debug("setting up SQL for database type " + dbInfo);
        if (dbInfo.contains("Derby")) {
            db_Derby = true;
            SurveyLog.debug("Note: derby mode");
            db_UnicodeType =             java.sql.Types.VARCHAR;
        } else if (dbInfo.contains("MySQL")) {
            System.err.println("Note: mysql mode");
            db_Mysql = true;
            DB_SQL_IDENTITY = "AUTO_INCREMENT PRIMARY KEY";
            DB_SQL_BINCOLLATE = " COLLATE latin1_bin ";
            DB_SQL_VARCHARXPATH = "TEXT(1000) CHARACTER SET latin1 "
                    + DB_SQL_BINCOLLATE;
            DB_SQL_BINTRODUCER = "_latin1";
            DB_SQL_WITHDEFAULT = "DEFAULT";
            DB_SQL_TIMESTAMP0 = "DATETIME";
            DB_SQL_LAST_MOD = " last_mod TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP ";
            DB_SQL_CURRENT_TIMESTAMP0 = "'1999-12-31 23:59:59'"; // NOW?
            DB_SQL_MIDTEXT = "TEXT(1024)";
            DB_SQL_BIGTEXT = "TEXT(16384)";
            DB_SQL_UNICODE = "BLOB";
            db_UnicodeType =             java.sql.Types.BLOB;
            DB_SQL_ALLTABLES = "show tables";
        } else {
            System.err.println("*** WARNING: Don't know what kind of database is "
                    + dbInfo  + " - might be interesting!");
        }
    }
    public void doShutdown() throws SQLException {
		datasource = null;
		if(this.db_number_open>0) {
		    System.err.println("DBUtils: removing my instance. " + this.db_number_open + " still open?\n"+tracker);
		}
		if(tracker!=null) tracker.clear();
		instance = null;
	}

	/**
	 * @deprecated Use {@link #getDBConnection()} instead
	 */
	public final Connection getDBConnection(SurveyMain surveyMain) {
		return getDBConnection();
	}
	
	public final Connection getDBConnection() {
		return getDBConnection("");
	}

	/**
	 * @deprecated Use {@link #getDBConnection(String)} instead
	 */
	public final Connection getDBConnection(SurveyMain surveyMain, String options) {
		return getDBConnection(options);
	}
	
	long lastMsg = -1;
	private int db_max_open=0;
	
	
	public Connection getDBConnection(String options) {
		try {
			db_max_open=Math.max(db_max_open, db_number_open++);
			db_number_used++;

			if(DEBUG) {
				long now = System.currentTimeMillis();
				if(now-lastMsg > (DEBUG_QUICKLY?6000:3600000) /*|| (db_number_used==5000)*/) {
					lastMsg=now;
					System.err.println("DBUtils: "+ db_number_open+" open, "+ db_max_open+" max,  " + db_number_used+" used. " + StackTracker.currentStack());
				}
			}

			Connection c = datasource.getConnection();
			if(db_Derby) {
				c.setAutoCommit(false);
			}
			if(SurveyMain.isUnofficial()&&tracker!=null) tracker.add(c);
			return c;
		} catch (SQLException se) {
			se.printStackTrace();
			SurveyMain.busted("Fatal in getDBConnection", se);
			return null;
		}
	}

	void setupDBProperties(SurveyMain surveyMain, CLDRConfig survprops) {
//		db_driver = cldrprops.getProperty("CLDR_DB_DRIVER",
//				"org.apache.derby.jdbc.EmbeddedDriver");
//		db_protocol = cldrprops.getProperty("CLDR_DB_PROTOCOL", "jdbc:derby:");
//		CLDR_DB_U = cldrprops.getProperty("CLDR_DB_U", null);
//		CLDR_DB_P = cldrprops.getProperty("CLDR_DB_P", null);
//		CLDR_DB = survprops.getProperty("CLDR_DB", "cldrdb");
//		dbDir = new File(SurveyMain.cldrHome, CLDR_DB);
//		cldrdb = survprops.getProperty("CLDR_DB_LOCATION",
//				dbDir.getAbsolutePath());
		CLDR_DB_CREATESUFFIX = survprops.getProperty("CLDR_DB_CREATESUFFIX",
				";create=true");
		CLDR_DB_SHUTDOWNSUFFIX = survprops.getProperty(
				"CLDR_DB_SHUTDOWNSUFFIX", "jdbc:derby:;shutdown=true");
	}

	public void startupDB(SurveyMain sm,
			CLDRProgressIndicator.CLDRProgressTask progress) {
	    System.err.println("StartupDB: datasource="+ datasource);
	    if(datasource == null) {
	        throw new RuntimeException("JNDI required: http://cldr.unicode.org/development/running-survey-tool/cldr-properties/db");
	    }

		progress.update("Using datasource..."+dbInfo); // restore

	}
	/**
	 * Shortcut for certain statements.
	 * @param conn
	 * @param str
	 * @return
	 * @throws SQLException
	 */
    public static final PreparedStatement prepareForwardReadOnly(Connection conn, String str) throws SQLException {
    	return conn.prepareStatement(str,ResultSet.TYPE_FORWARD_ONLY,ResultSet.CONCUR_READ_ONLY);
    }

    /**
     * prepare statements for this connection 
     * @throws SQLException 
     **/ 
    public static final PreparedStatement prepareStatementForwardReadOnly(Connection conn, String name, String sql) throws SQLException {
    	PreparedStatement ps = null;
    	try {       
    		ps = prepareForwardReadOnly(conn, sql);
    	} finally {
    		if(ps==null) {
    			System.err.println("Warning: couldn't initialize "+name+" from " + sql);
    		}
    	}
    	//            if(false) System.out.println("EXPLAIN EXTENDED " + sql.replaceAll("\\?", "'?'")+";");
    	//        } catch ( SQLException se ) {
    	//            String complaint = "Vetter:  Couldn't prepare " + name + " - " + DBUtils.unchainSqlException(se) + " - " + sql;
    	//            logger.severe(complaint);
    	//            throw new RuntimeException(complaint);
    	//        }
    	return ps;
    }
    
    /**
     * prepare statements for this connection 
     * @throws SQLException 
     **/ 
    public static final PreparedStatement prepareStatement(Connection conn, String name, String sql) throws SQLException {
    	PreparedStatement ps = null;
    	try {       
    		ps =  conn.prepareStatement(sql);
    	} finally {
    		if(ps==null) {
    			System.err.println("Warning: couldn't initialize "+name+" from " + sql);
    		}
    	}
    	//            if(false) System.out.println("EXPLAIN EXTENDED " + sql.replaceAll("\\?", "'?'")+";");
    	//        } catch ( SQLException se ) {
    	//            String complaint = "Vetter:  Couldn't prepare " + name + " - " + DBUtils.unchainSqlException(se) + " - " + sql;
    	//            logger.severe(complaint);
    	//            throw new RuntimeException(complaint);
    	//        }
    	return ps;
    }
	/**
	 * Close all of the objects in order, if not null. Knows how to close Connection, Statement, ResultSet, otherwise you'll get an IAE.
	 * @param a1
	 * @throws SQLException
	 */
	public static void close(Object... list) {
		for(Object o : list) {
//			if(o!=null) {
//				System.err.println("Closing " + an(o.getClass().getSimpleName())+" " + o.getClass().getName());
//			}
		    try {
    			if(o == null) {
    				continue;
    			} else if(o instanceof Connection ) {
    				DBUtils.closeDBConnection((Connection) o);
    			} else if (o instanceof Statement) {
    				((Statement)o).close();
    			} else if (o instanceof ResultSet) {
    				((ResultSet)o).close();
    			} else if(o instanceof DBCloseable) {
    				((DBCloseable)o).close();
    			} else {
    				throw new IllegalArgumentException("Don't know how to close "
    				        +an(o.getClass().getSimpleName())+" " + o.getClass().getName());
    			}
		    } catch (SQLException e) {
	            System.err.println(unchainSqlException(e));
	        }
		}
	}

	private static final UnicodeSet vowels = new UnicodeSet("[aeiouAEIOUhH]");
	/**
	 * Print A or AN appropriately.
	 * @param str
	 * @return
	 */
	private static String an(String str) {
		boolean isVowel = vowels.contains(str.charAt(0));
		return isVowel?"an":"a";
	}
	public boolean hasDataSource() {
		return(datasource!=null);
	}
    /**
	 * @param conn
	 * @param sql
	 * @param args
	 * @return
	 * @throws SQLException
	 */
	public static PreparedStatement prepareStatementWithArgs(Connection conn, String sql,
			Object... args) throws SQLException {
		PreparedStatement ps;
		ps = conn.prepareStatement(sql);
		
//		while (args!=null&&args.length==1&&args[0] instanceof Object[]) {
//			System.err.println("Unwrapping " + args + " to " + args[0]);
//		}
		setArgs(ps, args);
		return ps;
	}
    /**
     * @param ps
     * @param args
     * @throws SQLException
     */
    private static void setArgs(PreparedStatement ps, Object... args) throws SQLException {
        if(args!=null) {
			for(int i=0;i<args.length;i++) {
				Object o = args[i];
				if(o instanceof String) {
					ps.setString(i+1, (String)o);
				} else if(o instanceof Integer) {
					ps.setInt(i+1, (Integer)o);
				} else if(o instanceof CLDRLocale) { /* toString compatible things */
					ps.setString(i+1, ((CLDRLocale) o).getBaseName());
				} else {
					System.err.println("DBUtils: Warning: using toString for unknown object " + o.getClass().getName());
					ps.setString(i+1, o.toString());
				}
			}
		}
    }
    
	public static String[][] resultToArrayArray(ResultSet rs) throws SQLException {
		ArrayList<String[]> al = new ArrayList<String[]>();
		while(rs.next()) {
			al.add(arrayOfResult(rs));
		}
		return al.toArray(new String[al.size()][]);
	}
	public static Object[][] resultToArrayArrayObj(ResultSet rs) throws SQLException {
		ArrayList<Object[]> al = new ArrayList<Object[]>();
		ResultSetMetaData rsm = rs.getMetaData();
		int colCount =rsm.getColumnCount();
		while(rs.next()) {
			al.add(arrayOfResultObj(rs,colCount,rsm));
		}
		return al.toArray(new Object[al.size()][]);
	}
	@SuppressWarnings("rawtypes")
	private Map[] resultToArrayAssoc(ResultSet rs) throws SQLException {
		ResultSetMetaData rsm = rs.getMetaData();
		ArrayList<Map<String,Object>> al = new ArrayList<Map<String,Object>>();
		while(rs.next()) {
			al.add(assocOfResult(rs,rsm));
		}
		return al.toArray(new Map[al.size()]);
	}
	
	
	private Map<String, Object> assocOfResult(ResultSet rs,ResultSetMetaData rsm) throws SQLException {
		Map<String,Object> m = new HashMap<String,Object>(rsm.getColumnCount());
		
		for(int i=1;i<=rsm.getColumnCount();i++) {
			Object obj = extractObject(rs, rsm, i);
			m.put(rsm.getColumnName(i), obj);
		}
		
		return m;
	}
    /**
     * @param rs
     * @param rsm
     * @param i
     * @return
     * @throws SQLException
     */
    private static Object extractObject(ResultSet rs, ResultSetMetaData rsm, int i) throws SQLException {
        Object obj = null;
        int colType = rsm.getColumnType(i);
        if(colType==java.sql.Types.BLOB) {
        	obj=DBUtils.getStringUTF8(rs, i);
        } else if(colType == java.sql.Types.TIMESTAMP) {
            obj=rs.getTimestamp(i);
        } else if(colType == java.sql.Types.DATE) {
            obj=rs.getDate(i);
        } else { // generic
        	obj=rs.getObject(i);
        	if(obj!=null && obj.getClass().isArray()) {
        		obj=DBUtils.getStringUTF8(rs, i);
        	}
        }
        return obj;
    }

	public static String sqlQuery(Connection conn, String sql, Object... args) throws SQLException {
		return sqlQueryArray(conn,sql,args)[0];
	}

	public static String[] sqlQueryArray(Connection conn, String sql, Object... args) throws SQLException {
		return sqlQueryArrayArray(conn,sql,args)[0];
	}
	public static String[][] sqlQueryArrayArray(Connection conn, String str, Object... args) throws SQLException {
		PreparedStatement ps= null;
		ResultSet rs  = null;
		try {
			ps = prepareStatementWithArgs(conn, str, args);
			
			rs = ps.executeQuery();
			return resultToArrayArray(rs);
		} finally {
			DBUtils.close(rs,ps);
		}
	}
	public static Object[][] sqlQueryArrayArrayObj(Connection conn, String str, Object... args) throws SQLException {
		PreparedStatement ps= null;
		ResultSet rs  = null;
		try {
			ps = prepareStatementWithArgs(conn, str, args);
			
			rs = ps.executeQuery();
			return resultToArrayArrayObj(rs);
		} finally {
			DBUtils.close(rs,ps);
		}
	}
	public static int sqlUpdate(Connection conn, String str, Object... args) throws SQLException {
		PreparedStatement ps= null;
		try {
			ps = prepareStatementWithArgs(conn, str, args);

			return(ps.executeUpdate());
		} finally {
			DBUtils.close(ps);
		}
	}
	@SuppressWarnings("rawtypes")
	public Map[] sqlQueryArrayAssoc(Connection conn, String sql,
			Object... args) throws SQLException {
		PreparedStatement ps= null;
		ResultSet rs  = null;
		try {
			ps = prepareStatementWithArgs(conn, sql, args);
			
			rs = ps.executeQuery();
			return resultToArrayAssoc(rs);
		} finally {
			DBUtils.close(rs,ps);
		}
	}		
	private static String[] arrayOfResult(ResultSet rs) throws SQLException {
		ResultSetMetaData rsm = rs.getMetaData();
		String ret[] = new String[rsm.getColumnCount()];
		for(int i=0;i<ret.length;i++) {
			ret[i]=rs.getString(i+1);
		}
		return ret;
	}
	private static Object[] arrayOfResultObj(ResultSet rs, int colCount, ResultSetMetaData rsm) throws SQLException {
		Object ret[] = new Object[colCount];
		for(int i=0;i<ret.length;i++) {
		    Object obj = extractObject(rs,rsm,i+1);
		    ret[i]=obj;
		}
		return ret;
	}
	
	/**
	 * Interface to an object that contains a held Connection
	 * @author srl
	 *
	 */
	public interface ConnectionHolder {
		/**
		 * @return alias to held connection
		 */
		public Connection getConnectionAlias();
	}
	/**
	 * Interface to an object that DBUtils.close can close.
	 * @author srl
	 *
	 */
	public interface DBCloseable {
		/**
		 * Close this object
		 */
		public void close() throws SQLException;
	}
	
	public static void writeCsv(ResultSet rs, Writer out) throws SQLException, IOException {
        ResultSetMetaData rsm = rs.getMetaData();
        int cc = rsm.getColumnCount();
        for(int i=1;i<=cc;i++) {
        	if(i>1) {
				out.write(',');
        	}
			WebContext.csvWrite(out, rsm.getColumnName(i).toUpperCase());
        }
		out.write('\r');
		out.write('\n');
		
		while(rs.next()) {
            for(int i=1;i<=cc;i++) {
            	if(i>1) {
    				out.write(',');
            	}
                String v;
                try {
                    v = rs.getString(i);
                } catch(SQLException se) {
                    if(se.getSQLState().equals("S1009")) {
                        v="0000-00-00 00:00:00";
                    } else {
                    	throw se;
                    }
                }
                if(v != null) {
                    if(rsm.getColumnType(i)==java.sql.Types.LONGVARBINARY) {
                        String uni = DBUtils.getStringUTF8(rs, i);
                        WebContext.csvWrite(out, uni);
                    } else {
                        WebContext.csvWrite(out, v);
                    }
                }
            }
    		out.write('\r');
    		out.write('\n');
        }
	}
    public static JSONObject getJSON(ResultSet rs) throws SQLException, IOException, JSONException {
        ResultSetMetaData rsm = rs.getMetaData();
        int cc = rsm.getColumnCount();
        JSONObject ret = new JSONObject();
        JSONObject header = new JSONObject();
        JSONArray data  = new JSONArray();
        
        int hasxpath = -1;
        
        for(int i=1;i<=cc;i++) {
            String colname = rsm.getColumnName(i).toUpperCase();
            if(colname.equals("XPATH")) hasxpath=i;
            header.put(colname, i-1);
        }
        if(hasxpath>=0) {
            header.put("XPATH_STRHASH",cc);
        }
        
        ret.put("header", header);
        
        while(rs.next()) {
            JSONArray item = new JSONArray();
            String xpath = null;
            for(int i=1;i<=cc;i++) {
                String v;
                try {
                    v = rs.getString(i);
                    if(i==hasxpath) {
                        xpath = v;
                    }
                } catch(SQLException se) {
                    if(se.getSQLState().equals("S1009")) {
                        v="0000-00-00 00:00:00";
                    } else {
                        throw se;
                    }
                }
                if(v != null) {
                    if(rsm.getColumnType(i)==java.sql.Types.LONGVARBINARY) {
                        String uni = DBUtils.getStringUTF8(rs, i);
                        item.put(uni);
                    } else {
                        item.put(v);
                    }
                } else {
                    item.put(false);
                }
            }
            if(hasxpath>=0 && xpath!=null) {
                item.put(XPathTable.getStringIDString(xpath)); // add XPATH_STRHASH column
            }
            data.put(item);
        }
        ret.put("data",data);
        return ret;
    }
    public static JSONObject queryToJSON(String string, Object... args) throws SQLException, IOException, JSONException {
        Connection conn = null;
        PreparedStatement s = null;
        ResultSet rs = null;
        try {
            conn = getInstance().getDBConnection();
            s = DBUtils.prepareForwardReadOnly(conn, string);
            setArgs(s, args);
            rs = s.executeQuery();
            return getJSON(rs);
        } finally {
            close(rs,s,conn);
        }
    }
}
