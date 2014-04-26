package co.arkfall.play;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Random;
import java.util.UUID;
import java.util.logging.Logger;

import net.milkbowl.vault.permission.Permission;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public class ArkSync extends JavaPlugin implements Listener {
	private static final String[] rankNames = {"Unregistered","Builder","Contractor","Mason","Architect","Engineer"};
	private static final int[] rankTimes = {0,0,1,3,6,12};
	public static Permission perms = null;
	private static MySQL db;
	private Connection con;
	private Logger log;

	public void onDisable() {
		try {
			con.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public void onEnable() {
		// Save a copy of the default config.yml if one is not there
		log = getLogger();
		this.saveDefaultConfig();

		db = new MySQL(this.getConfig().getString("config.hostname"), this.getConfig().getString("config.portnmbr"), this.getConfig().getString("config.database"), this.getConfig().getString("config.username"), this.getConfig().getString("config.password"), this);
		con = db.open();
		if(con==null) {
			log.severe("connection to db is null, plugin probably won't work"); 
		}

		fillUUIDs(false);
		getServer().getPluginManager().registerEvents(this, this);

		setupPermissions();
	}

	private void fillUUIDs(boolean silent) {
		// TODO Auto-generated method stub
		try {
			ResultSet res = con.createStatement().executeQuery("SELECT `user_nicename` FROM (`wp_users` LEFT JOIN `UUIDs` ON `wp_users`.`ID`=`UUIDs`.`user_ID`) WHERE `UUID` IS NULL");
			
			while (res.next()) {
				@SuppressWarnings("deprecation")
				UUID uuid = Bukkit.getOfflinePlayer(res.getString(1)).getUniqueId();
				if (uuid == null) {
					if (!silent)
						log.warning("Player \"" + res.getString(1) + "\" in database does not exist in Mojang servers. Ignoring.");
					continue;
				}
				updateUUID(res.getString(1));
			}
		}
		catch (SQLException e) {
			e.printStackTrace();
		}
	}
	
	private void updateUUID(String player) throws SQLException {
		con.createStatement().execute("INSERT INTO `UUIDs` (`user_ID`, `UUID`) SELECT `ID`, '" + UUIDtoString(Bukkit.getOfflinePlayer(player).getUniqueId()) + "' FROM `wp_users` WHERE `user_nicename`='" + player + "'");
	}
	
	private ResultSet getUserByUUID(UUID uuid) throws SQLException {
		return con.createStatement().executeQuery("SELECT * FROM (`wp_users` LEFT JOIN `UUIDs` ON `wp_users`.`ID`=`UUIDs`.`userID`)");
	}

	/*private static String registerPlayer(Player player, String email) throws SQLException {
		Statement s = con.createStatement();

		String password = Integer.toHexString((new Random()).nextInt());

		s.execute("INSERT INTO `wp_users` (`user_login`, `user_pass`, `user_nicename`, `user_email`, `user_registered`, `display_name`) VALUES ('" + 
				player.getName() + "', " +
				"MD5('" + password + "'), '" +
				player.getName() + "', '" +
				email + "', '" +
				new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()) + "', '" +
				player.getName() + "', '" +
				UUIDtoString(player.getUniqueId()) + "')"
				);

		int id;
		ResultSet res = s.executeQuery("SELECT `ID` FROM `wp_users` WHERE `UUID`='" + UUIDtoString(player.getUniqueId()) + "'");
		res.next();
		id = res.getInt(1);

		s.execute("INSERT INTO `wp_usermeta` (`user_id`, `meta_key`, `meta_value`) VALUES ('" +
				id + "', 'wp_capabilities', 'a:1:{s:10:\"subscriber\";b:1;}')");
		s.execute("INSERT INTO `wp_usermeta` (`user_id`, `meta_key`, `meta_value`) VALUES ('" +
				id + "', 'wp_user_level', '0')");
		res.close();
		return password;
	}*/

	@EventHandler
	public void onPlayerJoin(PlayerJoinEvent event) {
		Player player = event.getPlayer();
		updateRank(player);
	}

	private boolean updateRank(Player player) {
		boolean returnV = false;
		if (getRank(player, false).equals("Default")) {
			returnV = register(player);
		}
		if (!getRank(player, false).equals("Default")) {
			Date date = null;
			try {
				date = parseDate(getRegistrationDate(player));
			} catch (SQLException e) {
				player.sendMessage("" + ChatColor.RED + ChatColor.BOLD + "ERROR: Could not get register date.");
				log.severe("ERROR: Could not get register date for player " + player.getName() + ".");
				e.printStackTrace();
				return false;
			}
			if (date != null) {
				int rankFromMonthsNum = getRankFromMonths(date);
				int rankNum = rankLookup(getRank(player, true));
				if (rankFromMonthsNum > rankNum && rankNum != -1) {
					int months = rankTimes[rankFromMonthsNum];
					setGroup(rankNames[rankFromMonthsNum] + (isPlus(player) ? "Plus" : ""), player, true);
					player.sendMessage("" + ChatColor.GREEN + "You have been on the server for more than " + (months != 1 ? months + " months" : "a month") + "! Your rank is now " + rankNames[rankFromMonthsNum] + (isPlus(player) ? "+" : "") + ".");
					return true;
				}
			}
		}
		return returnV;
	}

	private static boolean isPlus(Player player) {
		String rank = perms.getPrimaryGroup(player);
		if (rank.endsWith("Plus")) {
			return true;
		}
		else {
			return false;
		}
	}

	private static boolean validateEmail(String email) {
		return email.matches("[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\\.[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+)*@(?:[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?\\.)+(?:[A-Za-z]{2}|com|org|net|edu|gov|mil|biz|info|mobi|name|aero|asia|jobs|museum)");
	}

	private static String getRank(Player player, boolean chopOffPlus) {
		String rank = perms.getPrimaryGroup(player);
		if (rank.endsWith("Plus") && chopOffPlus) {
			rank = rank.substring(0, rank.length() - 4);
		}
		return rank;
	}

	private String getFormattedTimeOnServer(Player player) {
		Date date;
		try {
			date = parseDate(getRegistrationDate(player));
			int years = getYears(date);
			int months = getMonthsModYears(date);
			int days = getDaysModMonths(date);
			return ((years > 0 ? years + " year" + (years != 1 ? "s" : "") + ", " : "") + (months > 0 || years > 0 ? months + " month" + (months != 1 ? "s" : "") + (years > 0 ? "," : "") + " and " : "") + days + " day" + (days != 1 ? "s" : ""));
		} catch (SQLException e) {
			e.printStackTrace();
			return "ERROR!";
		}

	}

	private static int rankLookup(String rankName)
	{
		for (int i = 0; i < rankNames.length; i++) {
			if (rankNames[i].matches(rankName)) return i;
		}
		return -1;
	}

	private static int getRankFromMonths(Date dateRegistered)
	{
		if (dateRegistered == null) return 0;	//return Unregistered if they never registered
		int months = getMonths(dateRegistered);
		if (months < 1) return 1;				// return Builder
		else if (months < 3) return 2;			// return Contractor
		else if (months < 6) return 3;			// return Mason
		else if (months < 12) return 4;			// return Architect
		else return 5;							// return Engineer
	}

	private static int getDaysModMonths(Date dateRegistered)
	{
		// get current date
		Date today = new Date();

		long difference = today.getTime() - dateRegistered.getTime();

		return (int)(difference / (1000 * 3600 * 24) % 30.4375);
	}

	private static long getSeconds(Date dateRegistered)
	{
		// get current date
		Date today = new Date();

		long difference = today.getTime() - dateRegistered.getTime();

		return difference / 1000;
	}

	private static int getMonths(Date dateRegistered)
	{
		// get current date
		Date today = new Date();

		long difference = today.getTime() - dateRegistered.getTime();

		return (int)(difference / (1000 * 3600 * 24 * 30.4375));
	}

	private static int getMonthsModYears(Date dateRegistered)
	{
		// get current date
		Date today = new Date();

		long difference = today.getTime() - dateRegistered.getTime();

		return (int)(difference / (1000 * 3600 * 24 * 30.4375) % 12);
	}

	private static int getYears(Date dateRegistered)
	{
		// get current date
		Date today = new Date();

		long difference = today.getTime() - dateRegistered.getTime();

		return (int)(difference / (1000 * 3600 * 24 * 30.4375 * 12));
	}

	private String getTimeToNextRank(Player player) throws SQLException {
		int rank = rankLookup(getRank(player, true));
		if (rank > 0 && rank < 5) {
			long seconds = getSeconds(parseDate(getRegistrationDate(player)));

			long secondsForNextRank = (long) (rankTimes[rank + 1] * 30.4375 * 24 * 3600);

			long difference = secondsForNextRank - seconds;

			int monthsDifference = (int) (difference / 3600 / 24 / 30.4375);
			int daysDifference = (int) (difference / 3600 / 24 % 30.4375);

			return (monthsDifference > 0 ? (monthsDifference != 1 ? monthsDifference + " months" : "a month") + " and " : "" ) + (daysDifference != 1 ? daysDifference + " days" : "a day");
		}
		else return "";
	}

	private static Date parseDate(String input)
	{
		// Sets the date format used
		DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

		// parses date
		try {
			return dateFormat.parse(input);
		}
		catch (ParseException e) {
			e.printStackTrace();
			return null;
		}
	}

	private String encodeDate(Date date) {
		DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

		return dateFormat.format(date);
	}

	private String getRegistrationDate(Player player) throws SQLException {
		if(!db.checkConnection() || con == null || !con.isValid(5)) {
			con = db.open();
			if(con==null) {
				log.severe("connection to db is null, exiting method to avoid exception"); 
				return null;
			}
		}
		Statement s = con.createStatement();
		try {
			ResultSet res = getUserByUUID(player.getUniqueId());
			if(res.next()) {
				return res.getString("user_registered");
			}
			else {
				player.sendMessage("" + ChatColor.RED + ChatColor.BOLD + "ERROR: Non-unregistered player " + player.getName() + "(" + player.getUniqueId() + ") does not exist in SQL server.");
				log.severe("ERROR: Player " + player.getName() + " does not exist in SQL server. Their group is " + getRank(player, false) + ".");
			}

			res.close();
		}
		catch (SQLException e) {
			player.sendMessage("" + ChatColor.RED + ChatColor.BOLD + "ERROR: Could not pull register date from SQL server for player " + player.getName() + ".");
			log.severe("ERROR: Could not pull register date from SQL server.");
			e.printStackTrace();
		}

		return null;

	}

	private boolean setupPermissions() {
		RegisteredServiceProvider<Permission> rsp = getServer().getServicesManager().getRegistration(Permission.class);
		perms = rsp.getProvider();
		return perms != null;
	}

	private boolean register(Player player) {
		try {
			boolean hasRegistered = userRegistered(player);
			if(!hasRegistered) {
				player.sendMessage(ChatColor.ITALIC +""+ ChatColor.GREEN + "You have not yet registered on our website. Please go to www.arkfall.co to register.");
				return false;
			}
			else {
				//player registered, set their rank to Builder
				setGroup("Builder", player, true);
				player.sendMessage(ChatColor.ITALIC +""+ ChatColor.GREEN + "You have been registered! Your rank is now Builder.");
				return true;
			}
		}
		catch (SQLException e) {
			e.printStackTrace();
			player.sendMessage(ChatColor.RED + "An error occured while trying to check your registration. Please contact a moderator.");
			return false;
		}
	}

	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args){
		if (cmd.getName().equalsIgnoreCase("arkrank")) {
			if (args.length > 1) {
				sender.sendMessage(ChatColor.RED + "Too many arguments!");
				return false;
			}
			else if (args.length == 1 || sender instanceof Player) {

				Player target;
				if (args.length == 0) {
					target = (Player) sender;
				}
				else {
					target = Bukkit.getPlayer(args[0]);
				}

				if (target == null) {
					if (Bukkit.getOfflinePlayer(args[0]).hasPlayedBefore()) {
						sender.sendMessage(ChatColor.RED + "Player must be online.");
					}
					else {
						sender.sendMessage(ChatColor.RED + "Player does not exist!");
					}
				}
				else {
					if (sender == target || sender.hasPermission("arksync.arkrankothers")) {
						if (getRank(target, false).equals("Default")) {
							sender.sendMessage(ChatColor.RED + (target == sender ? "You are" : target.getName() + " is") + " not registered.");
						}
						else {
							sender.sendMessage(ChatColor.YELLOW + (target == sender ? "Your" : target.getName() + "'s") + " current rank is " + getRank(target, true) + (isPlus(target) ? "+" : "") + ".");
							sender.sendMessage(ChatColor.YELLOW + (target == sender ? "You have" : target.getName() + " has") + " been on Arkfall for " + (getFormattedTimeOnServer(target).equals("0 days") ? "less than a day" : getFormattedTimeOnServer(target)) + ".");

							try {
								String timeToNextRank = getTimeToNextRank(target);
								if (!timeToNextRank.isEmpty()) {
									sender.sendMessage(ChatColor.YELLOW + (sender == target ? "You" : target.getName()) + " will be promoted to " + rankNames[rankLookup(getRank(target, false)) + 1] + " " + (timeToNextRank.equals("0 days") ? "within a day" : "in " + timeToNextRank) + ".\nTo check out all of Arkfall's ranks, type /ranks.");
								}
							}
							catch (SQLException e) {
								target.sendMessage("" + ChatColor.RED + ChatColor.BOLD + "ERROR: Could not get time to next rank for player \"" + target.getName() + "\".");
								log.severe("ERROR: Could not get time to next rank for player \"" + target.getName() + "\".");
								e.printStackTrace();
							}
						}
					}
					else {
						sender.sendMessage(ChatColor.RED + "You do not have permission to perform this command on another player.");
					}
				}
				return true;
			}
			else {
				sender.sendMessage(ChatColor.RED + "Must give a player as an argument or use this command as player.");
			}
		}
		else if (cmd.getName().equalsIgnoreCase("arkrankset")) {
			if (args.length != 2) {
				sender.sendMessage(ChatColor.RED + "Must give exactly two arguments");
			}
			else if (Bukkit.getPlayer(args[0]) == null) {
				if (Bukkit.getOfflinePlayer(args[0]) != null) {
					sender.sendMessage(ChatColor.RED + "Player must be online.");
				}
				else {
					sender.sendMessage(ChatColor.RED + "Invalid player!");
				}
			}
			else if (!checkDateFormat(args[1])) {
				sender.sendMessage(ChatColor.RED + "Invalid date!");
			}
			else {
				try {
					setDate(Bukkit.getPlayer(args[0]), parseDate(args[1] + " 00:00:00"));
					sender.sendMessage(ChatColor.YELLOW + "Set " + args[0] + "'s registration date to " + args[1] + " 00:00:00.");

				} catch (SQLException e) {
					e.printStackTrace();
				}
				return true;
			}
		}
		else if (cmd.getName().equalsIgnoreCase("updaterank")) {
			if (args.length > 1) {
				sender.sendMessage(ChatColor.RED + "Too many arguments!");
				return false;
			}
			else if (args.length == 1 || sender instanceof Player) {

				Player target;
				if (args.length == 0) {
					target = (Player) sender;
				}
				else {
					target = Bukkit.getPlayer(args[0]);
				}

				if (target == null) {
					if (Bukkit.getOfflinePlayer(args[0]).hasPlayedBefore()) {
						sender.sendMessage(ChatColor.RED + "Player must be online.");
					}
					else {
						sender.sendMessage(ChatColor.RED + "Player does not exist!");
					}
				}
				else {
					if (sender == target || sender.hasPermission("arksync.updaterankothers")) {
						sender.sendMessage(ChatColor.YELLOW + "Checking for rank updates...");
						boolean success = updateRank(target);
						if (success) {
							if (sender != target) {
								sender.sendMessage(ChatColor.YELLOW + target.getName() + "'s" + " rank was updated.");
							}
						}
						else {
							if (sender == target) {
								sender.sendMessage(ChatColor.YELLOW + (target == sender ? "Your" : target.getName() + "'s") + " rank could not be updated.");								
							}
						}
					}
					else {
						sender.sendMessage(ChatColor.RED + "You do not have permission to perform this command on another player.");
					}
				}
				return true;
			}
			else {
				sender.sendMessage(ChatColor.RED + "Must give a player as an argument or use this command as player.");
			}
		}
		/*else if (cmd.getName().equalsIgnoreCase("register")) {
			if (! (sender instanceof Player)) {
				sender.sendMessage(ChatColor.RED + "You must me a player to use this command.");
				return false;
			}
			else if (args.length > 1) {
				sender.sendMessage(ChatColor.RED + "Too many arguments!");
				return false;
			}
			else if (args.length < 1) {
				sender.sendMessage(ChatColor.RED + "You need to supply an email address.");
				//TODO print help instead of error
				return false;
			}
			else if (! validateEmail(args[0])) {
				sender.sendMessage(ChatColor.RED + "Invalid email.");
			}
			else
				try {
					if (userRegistered((Player) sender)) {
						sender.sendMessage(ChatColor.RED + "You must be unregistered to use this command.");
					}
					else {
						String password = registerPlayer((Player) sender, args[0]);
						sender.sendMessage(ChatColor.GREEN + "Your password is " + password);
						register((Player) sender);
					}
				} catch (SQLException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
		}*/
		return false;
	}

	private boolean checkDateFormat(String date) {
		return date.matches("\\d{4}(-\\d{2}){2}"); // checks if it's in the format yyyy-MM-dd
	}

	private static String UUIDtoString(UUID uuid) {
		return uuid.toString().replace("-", "");
	}

	private boolean userRegistered(Player player) throws SQLException {
		if(!db.checkConnection() || con == null || !con.isValid(5)) {
			con = db.open();
			if(con==null) {
				log.severe("connection to db is null, exiting method to avoid exception"); 
				return false;
			}
		}
		Statement s = con.createStatement();
		ResultSet res = getUserByUUID(player.getUniqueId());
		boolean returnV;
		if(res.next()) {
			returnV = true;
		}
		else {
			returnV = false;
		}
		res.close();
		return returnV;

	}

	private boolean setDate(Player player, Date date) throws SQLException {
		if(!db.checkConnection() || con == null || !con.isValid(5)) {
			con = db.open();
			if(con==null) {
				log.severe("connection to db is null, exiting method to avoid exception"); 
				return false;
			}
		}
		Statement s = con.createStatement();
		s.execute("UPDATE `wp_users` SET `user_registered` = '" + encodeDate(date) + "' WHERE `ID` IN (SELECT `userID` FROM `UUIDs` WHERE `UUID`='" + UUIDtoString(player.getUniqueId()) + "')");
		return false;
	}

	private boolean setGroup(String groupName, Player player, boolean n)
	{
		try
		{
			Bukkit.dispatchCommand(Bukkit.getServer().getConsoleSender(), new StringBuilder().append("manuadd ").append(player.getName()).append(" ").append(groupName).toString());
			if (n)
			{
				log.fine("Setting " + player.getName() + " to group " + groupName);
			}
			return true;

		}
		catch (Error e) { 
			log.severe(e.getMessage());
		}
		return false;
	}

	public Logger getPluginLogger() {
		return log;
	}
}
