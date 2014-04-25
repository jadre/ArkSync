package co.arkfall.play;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.logging.Logger;

import org.bukkit.plugin.java.JavaPlugin;



public class MySQL extends Database
{
	String user = "";
	String database = "";
	String password = "";
	String port = "";
	String hostname = "";
	Connection c = null;
	JavaPlugin plugin;
	Logger log;


	public MySQL(String hostname, String portnmbr, String database, String username, String password, JavaPlugin plugin) {
		this.hostname = hostname;
		this.port = portnmbr;
		this.database = database;
		this.user = username;
		this.password = password;
		this.plugin = plugin;
		log = ((ArkSync) plugin).getPluginLogger();
		//log.info(this.toString());
		
	}
	@Override
	public String toString() {
		return "MySQL [user=" + user + ", database=" + database + ", password="
				+ password + ", port=" + port + ", hostname=" + hostname
				+ ", c=" + c + ", plugin=" + plugin + "]";
	}
	public Connection open() {
		try {
			Class.forName("com.mysql.jdbc.Driver");
			this.c = DriverManager.getConnection("jdbc:mysql://" + this.hostname + ":" + this.port + "/" + this.database, this.user, this.password);
			return c;
		} catch (SQLException e) {
			log.severe("Could not connect to MySQL server! because: " + e.getMessage());
		} catch (ClassNotFoundException e) {
			log.severe("JDBC Driver not found!");
		}
		return this.c;
	}
	public boolean checkConnection() {
		if (this.c != null) {
			return true;
		}
		return false;
	}
	public Connection getConn() {
		return this.c;
	}
	public void closeConnection(Connection c) {
		c = null;
	}
}
