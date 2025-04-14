import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.EmbedBuilder;

import java.awt.Color;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

public class DiscordBot extends ListenerAdapter {

    private Connection economicConnection;
    private Connection playersConnection;
    private static final List<String> ALLOWED_USERS = Arrays.asList("632581944860999690", "455682532357177354"); // mokinasofficial и mrsmaylik

    public DiscordBot() throws SQLException {
        connectToDatabases();
    }

    private void connectToDatabases() throws SQLException {
        String economicUrl = "jdbc:mysql://uran.minerent.net:3306/s83687_economic?autoReconnect=true&useSSL=false";
        String economicUser = "u83687_CBVu9IOxUY";
        String economicPassword = "RXybOfBVvRm=JViQJB@Vcr7Z";
        economicConnection = DriverManager.getConnection(economicUrl, economicUser, economicPassword);
        economicConnection.setAutoCommit(true);

        String playersUrl = "jdbc:mysql://uran.minerent.net:3306/s83687_economic_players?autoReconnect=true&useSSL=false";
        String playersUser = "u83687_wxvlsshO2A";
        String playersPassword = "Yd!.Ao^7TYmfiNaNUr^dtCli";
        playersConnection = DriverManager.getConnection(playersUrl, playersUser, playersPassword);
        playersConnection.setAutoCommit(true);
    }

    private void reconnectIfNeeded(Connection connection, String url, String user, String password, String dbName) {
        try {
            if (connection == null || connection.isClosed() || !connection.isValid(2)) {
                System.out.println("Переподключение к базе " + dbName + "...");
                connection = DriverManager.getConnection(url, user, password);
                connection.setAutoCommit(true);
                System.out.println("Переподключение к базе " + dbName + " выполнено.");
            }
        } catch (SQLException e) {
            System.err.println("Ошибка при переподключении к базе " + dbName + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void reconnectEconomic() {
        reconnectIfNeeded(economicConnection,
                "jdbc:mysql://uran.minerent.net:3306/s83687_economic?autoReconnect=true&useSSL=false",
                "u83687_CBVu9IOxUY", "RXybOfBVvRm=JViQJB@Vcr7Z", "s83687_economic");
        economicConnection = economicConnection != null ? economicConnection : economicConnection;
    }

    private void reconnectPlayers() {
        reconnectIfNeeded(playersConnection,
                "jdbc:mysql://uran.minerent.net:3306/s83687_economic_players?autoReconnect=true&useSSL=false",
                "u83687_wxvlsshO2A", "Yd!.Ao^7TYmfiNaNUr^dtCli", "s83687_economic_players");
        playersConnection = playersConnection != null ? playersConnection : playersConnection;
    }

    public static void main(String[] args) throws Exception {
        String token = System.getenv("BOT_TOKEN");
        JDA jda = JDABuilder.createDefault(token)
                .enableIntents(GatewayIntent.GUILD_MEMBERS, GatewayIntent.MESSAGE_CONTENT)
                .addEventListeners(new DiscordBot())
                .build();

        jda.updateCommands().addCommands(
                Commands.slash("balance", "Check your currency balance"),
                Commands.slash("disbalance", "Check your Discord currency balance"),
                Commands.slash("account", "Manage your Minecraft account")
                        .addOption(OptionType.STRING, "action", "link or unlink", true)
                        .addOption(OptionType.STRING, "player", "Minecraft username", true)
                        .addOption(OptionType.STRING, "password", "Crypto password", true),
                Commands.slash("diseco", "Admin Discord currency commands")
                        .addOption(OptionType.STRING, "action", "give or take", true)
                        .addOption(OptionType.USER, "user", "Discord user", true)
                        .addOption(OptionType.NUMBER, "amount", "Amount", true),
                Commands.slash("economic", "Economy commands")
                        .addOption(OptionType.STRING, "action", "buy or sell", true)
                        .addOption(OptionType.STRING, "currency", "Currency name or ID", true)
                        .addOption(OptionType.NUMBER, "amount", "Amount", true),
                Commands.slash("market", "Show the currency market"),
                Commands.slash("eco", "Economy commands")
                        .addOption(OptionType.STRING, "action", "market, buy or sell", true)
                        .addOption(OptionType.STRING, "currency", "Currency name or ID", false)
                        .addOption(OptionType.NUMBER, "amount", "Amount", false)
        ).queue();
    }

    private String formatPercent(double percent) {
        return String.format("%.1f%%", percent);
    }

    private Color hexToColor(String hexColor) {
        try {
            if (hexColor != null && hexColor.matches("^#[0-9A-Fa-f]{6}$")) {
                return Color.decode(hexColor);
            }
        } catch (NumberFormatException e) {
            System.err.println("Неверный формат HEX цвета: " + hexColor);
        }
        return Color.WHITE;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        String command = event.getName();
        User user = event.getUser();
        String discordId = user.getId();
        String discordName = user.getAsTag();

        try {
            if (command.equals("account")) {
                reconnectPlayers();
                String action = event.getOption("action").getAsString().toLowerCase();
                String playerName = event.getOption("player").getAsString();
                String password = event.getOption("password").getAsString();

                if (action.equals("link")) {
                    String query = "SELECT crypto_password, discord_id FROM player_currencies WHERE player_name = ? AND currency_name = 'None' LIMIT 1";
                    PreparedStatement stmt = playersConnection.prepareStatement(query);
                    stmt.setString(1, playerName);
                    ResultSet rs = stmt.executeQuery();
                    if (!rs.next()) {
                        event.reply("Игрок " + playerName + " не найден или крипто-пароль не установлен!").setEphemeral(true).queue();
                        rs.close();
                        stmt.close();
                        return;
                    }
                    String storedPassword = rs.getString("crypto_password");
                    String currentDiscordId = rs.getString("discord_id");
                    rs.close();
                    stmt.close();
                    if (!password.equals(storedPassword)) {
                        event.reply("Неверный крипто-пароль!").setEphemeral(true).queue();
                        return;
                    }
                    if (currentDiscordId != null && !currentDiscordId.isEmpty()) {
                        event.reply("Аккаунт уже привязан! Сначала отвяжите его с помощью /account unlink.").setEphemeral(true).queue();
                        return;
                    }
                    String updateQuery = "UPDATE player_currencies SET discord_id = ?, discord_name = ? WHERE player_name = ? AND currency_name = 'None'";
                    PreparedStatement updateStmt = playersConnection.prepareStatement(updateQuery);
                    updateStmt.setString(1, discordId);
                    updateStmt.setString(2, discordName);
                    updateStmt.setString(3, playerName);
                    updateStmt.executeUpdate();
                    updateStmt.close();
                    event.reply("Аккаунт успешно привязан!").setEphemeral(true).queue();
                } else if (action.equals("unlink")) {
                    String query = "SELECT crypto_password, discord_id FROM player_currencies WHERE player_name = ? AND currency_name = 'None' LIMIT 1";
                    PreparedStatement stmt = playersConnection.prepareStatement(query);
                    stmt.setString(1, playerName);
                    ResultSet rs = stmt.executeQuery();
                    if (!rs.next()) {
                        event.reply("Игрок " + playerName + " не найден или крипто-пароль не установлен!").setEphemeral(true).queue();
                        rs.close();
                        stmt.close();
                        return;
                    }
                    String storedPassword = rs.getString("crypto_password");
                    String currentDiscordId = rs.getString("discord_id");
                    rs.close();
                    stmt.close();
                    if (!password.equals(storedPassword)) {
                        event.reply("Неверный крипто-пароль!").setEphemeral(true).queue();
                        return;
                    }
                    if (currentDiscordId == null || currentDiscordId.isEmpty()) {
                        event.reply("Аккаунт Discord не привязан!").setEphemeral(true).queue();
                        return;
                    }
                    String updateQuery = "UPDATE player_currencies SET discord_id = NULL, discord_name = NULL WHERE player_name = ? AND currency_name = 'None'";
                    PreparedStatement updateStmt = playersConnection.prepareStatement(updateQuery);
                    updateStmt.setString(1, playerName);
                    updateStmt.executeUpdate();
                    updateStmt.close();
                    event.reply("Аккаунт Discord успешно отвязан!").setEphemeral(true).queue();
                } else {
                    event.reply("Действие должно быть 'link' или 'unlink'!").setEphemeral(true).queue();
                }
                return;
            }

            if (command.equals("diseco")) {
                reconnectPlayers();
                if (!ALLOWED_USERS.contains(discordId)) {
                    event.reply("У вас нет прав на выполнение этой команды!").setEphemeral(true).queue();
                    return;
                }
                String action = event.getOption("action").getAsString().toLowerCase();
                User targetUser = event.getOption("user").getAsUser();
                String targetDiscordId = targetUser.getId();
                String targetDiscordName = targetUser.getAsTag();
                double amount = event.getOption("amount").getAsDouble();
                if (amount <= 0) {
                    event.reply("Количество должно быть положительным!").setEphemeral(true).queue();
                    return;
                }
                String targetQuery = "SELECT discord_balance, player_name FROM player_currencies WHERE discord_id = ? AND currency_name = 'None' LIMIT 1";
                PreparedStatement targetStmt = playersConnection.prepareStatement(targetQuery);
                targetStmt.setString(1, targetDiscordId);
                ResultSet targetRs = targetStmt.executeQuery();
                if (!targetRs.next()) {
                    event.reply("Пользователь " + targetDiscordName + " не привязал аккаунт Discord!").setEphemeral(true).queue();
                    targetRs.close();
                    targetStmt.close();
                    return;
                }
                double currentBalance = targetRs.getDouble("discord_balance");
                String playerName = targetRs.getString("player_name");
                targetRs.close();
                targetStmt.close();
                if (action.equals("give")) {
                    String updateQuery = "UPDATE player_currencies SET discord_balance = discord_balance + ? WHERE discord_id = ? AND currency_name = 'None'";
                    PreparedStatement updateStmt = playersConnection.prepareStatement(updateQuery);
                    updateStmt.setDouble(1, amount);
                    updateStmt.setString(2, targetDiscordId);
                    updateStmt.executeUpdate();
                    updateStmt.close();
                    event.reply("Выдано " + amount + " Discord-валюты пользователю " + targetDiscordName + " (игрок: " + playerName + ")").setEphemeral(true).queue();
                } else if (action.equals("take")) {
                    if (currentBalance < amount) {
                        event.reply("У пользователя " + targetDiscordName + " недостаточно Discord-валюты!").setEphemeral(true).queue();
                        return;
                    }
                    String updateQuery = "UPDATE player_currencies SET discord_balance = GREATEST(0, discord_balance - ?) WHERE discord_id = ? AND currency_name = 'None'";
                    PreparedStatement updateStmt = playersConnection.prepareStatement(updateQuery);
                    updateStmt.setDouble(1, amount);
                    updateStmt.setString(2, targetDiscordId);
                    updateStmt.executeUpdate();
                    updateStmt.close();
                    event.reply("Забрано " + amount + " Discord-валюты у пользователя " + targetDiscordName + " (игрок: " + playerName + ")").setEphemeral(true).queue();
                } else {
                    event.reply("Действие должно быть 'give' или 'take'!").setEphemeral(true).queue();
                }
                return;
            }

            if (command.equals("market") || (command.equals("eco") && event.getOption("action") != null && event.getOption("action").getAsString().toLowerCase().equals("market"))) {
                reconnectEconomic();
                String query = "SELECT id, name, color, current_price, last_change_percent FROM currencies";
                PreparedStatement stmt = economicConnection.prepareStatement(query);
                ResultSet rs = stmt.executeQuery();
                EmbedBuilder embed = new EmbedBuilder();
                embed.setTitle("Рынок валют");
                embed.setColor(Color.BLUE);
                boolean hasCurrencies = false;
                while (rs.next()) {
                    String name = rs.getString("name");
                    String hexColor = rs.getString("color");
                    double price = rs.getDouble("current_price");
                    double changePercent = rs.getDouble("last_change_percent");
                    String changeSymbol = changePercent > 0 ? "+" : changePercent < 0 ? "-" : "";
                    String fieldValue = String.format("Цена: %.2f$ (%s%.1f%%)", price, changeSymbol, Math.abs(changePercent));
                    embed.addField(name, fieldValue, true);
                    hasCurrencies = true;
                }
                rs.close();
                stmt.close();
                if (!hasCurrencies) {
                    embed.setDescription("На рынке нет валют!");
                }
                event.replyEmbeds(embed.build()).setEphemeral(true).queue();
                return;
            }

            // Проверка привязки аккаунта для остальных команд
            reconnectPlayers();
            String playerName = null;
            String query = "SELECT player_name FROM player_currencies WHERE discord_id = ? AND currency_name = 'None' LIMIT 1";
            PreparedStatement stmt = playersConnection.prepareStatement(query);
            stmt.setString(1, discordId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                playerName = rs.getString("player_name");
            }
            rs.close();
            stmt.close();

            if (playerName == null && !command.equals("disbalance")) {
                event.reply("Вы не привязали аккаунт! Используйте /account link [ник] [крипто-пароль]").setEphemeral(true).queue();
                return;
            }

            if (command.equals("balance")) {
                reconnectPlayers();
                String balanceQuery = "SELECT currency_name, amount FROM player_currencies WHERE player_name = ? AND currency_name != 'None'";
                PreparedStatement balanceStmt = playersConnection.prepareStatement(balanceQuery);
                balanceStmt.setString(1, playerName);
                ResultSet balanceRs = balanceStmt.executeQuery();
                StringBuilder response = new StringBuilder("Ваш баланс:\n");
                boolean hasBalance = false;
                while (balanceRs.next()) {
                    response.append(balanceRs.getString("currency_name")).append(": ").append(String.format("%.2f", balanceRs.getDouble("amount"))).append("\n");
                    hasBalance = true;
                }
                balanceRs.close();
                balanceStmt.close();
                if (!hasBalance) {
                    response.append("У вас нет валют!");
                }
                event.reply(response.toString()).setEphemeral(true).queue();
                return;
            }

            if (command.equals("disbalance")) {
                reconnectPlayers();
                String balanceQuery = "SELECT discord_balance FROM player_currencies WHERE discord_id = ? AND currency_name = 'None' LIMIT 1";
                PreparedStatement balanceStmt = playersConnection.prepareStatement(balanceQuery);
                balanceStmt.setString(1, discordId);
                ResultSet balanceRs = balanceStmt.executeQuery();
                double discordBalance = balanceRs.next() ? balanceRs.getDouble("discord_balance") : 0;
                balanceRs.close();
                balanceStmt.close();
                event.reply("Ваш Discord-баланс: " + String.format("%.2f", discordBalance)).setEphemeral(true).queue();
                return;
            }

            if (command.equals("economic") || command.equals("eco")) {
                reconnectEconomic();
                reconnectPlayers();
                String action = event.getOption("action").getAsString().toLowerCase();
                if (action.equals("market")) {
                    // Обработка /eco market уже выше
                    return;
                }
                String currencyInput = event.getOption("currency").getAsString();
                double amount = event.getOption("amount").getAsDouble();
                if (amount <= 0) {
                    event.reply("Количество должно быть положительным!").setEphemeral(true).queue();
                    return;
                }
                String currencyQuery = currencyInput.matches("\\d+") ?
                        "SELECT id, name, current_price FROM currencies WHERE id = ?" :
                        "SELECT id, name, current_price FROM currencies WHERE LOWER(name) = LOWER(?)";
                PreparedStatement currencyStmt = economicConnection.prepareStatement(currencyQuery);
                if (currencyInput.matches("\\d+")) {
                    currencyStmt.setInt(1, Integer.parseInt(currencyInput));
                } else {
                    currencyStmt.setString(1, currencyInput);
                }
                ResultSet currencyRs = currencyStmt.executeQuery();
                if (!currencyRs.next()) {
                    event.reply("Валюта '" + currencyInput + "' не найдена!").setEphemeral(true).queue();
                    currencyRs.close();
                    currencyStmt.close();
                    return;
                }
                int currencyId = currencyRs.getInt("id");
                String currencyName = currencyRs.getString("name");
                double price = currencyRs.getDouble("current_price");
                currencyRs.close();
                currencyStmt.close();

                if (action.equals("buy")) {
                    double cost = price * amount * 100000;
                    String balanceQuery = "SELECT discord_balance FROM player_currencies WHERE player_name = ? AND currency_name = 'None' LIMIT 1";
                    PreparedStatement balanceStmt = playersConnection.prepareStatement(balanceQuery);
                    balanceStmt.setString(1, playerName);
                    ResultSet balanceRs = balanceStmt.executeQuery();
                    double discordBalance = balanceRs.next() ? balanceRs.getDouble("discord_balance") : 0;
                    balanceRs.close();
                    balanceStmt.close();
                    if (discordBalance < cost) {
                        event.reply("Недостаточно Discord-валюты! Требуется: " + cost).setEphemeral(true).queue();
                        return;
                    }
                    String updateBalanceQuery = "UPDATE player_currencies SET discord_balance = discord_balance - ? WHERE player_name = ? AND currency_name = 'None'";
                    PreparedStatement updateBalanceStmt = playersConnection.prepareStatement(updateBalanceQuery);
                    updateBalanceStmt.setDouble(1, cost);
                    updateBalanceStmt.setString(2, playerName);
                    updateBalanceStmt.executeUpdate();
                    updateBalanceStmt.close();
                    String updateCurrencyQuery = "INSERT INTO player_currencies (player_name, currency_name, currency_id, amount) " +
                            "VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE amount = amount + ?";
                    PreparedStatement updateCurrencyStmt = playersConnection.prepareStatement(updateCurrencyQuery);
                    updateCurrencyStmt.setString(1, playerName);
                    updateCurrencyStmt.setString(2, currencyName);
                    updateCurrencyStmt.setInt(3, currencyId);
                    updateCurrencyStmt.setDouble(4, amount);
                    updateCurrencyStmt.setDouble(5, amount);
                    updateCurrencyStmt.executeUpdate();
                    updateCurrencyStmt.close();
                    event.reply("Куплено " + amount + " " + currencyName + " за " + cost + " Discord-валюты").setEphemeral(true).queue();
                } else if (action.equals("sell")) {
                    String balanceQuery = "SELECT amount FROM player_currencies WHERE player_name = ? AND currency_id = ? AND currency_name = ?";
                    PreparedStatement balanceStmt = playersConnection.prepareStatement(balanceQuery);
                    balanceStmt.setString(1, playerName);
                    balanceStmt.setInt(2, currencyId);
                    balanceStmt.setString(3, currencyName);
                    ResultSet balanceRs = balanceStmt.executeQuery();
                    double currentAmount = balanceRs.next() ? balanceRs.getDouble("amount") : 0;
                    balanceRs.close();
                    balanceStmt.close();
                    if (currentAmount < amount) {
                        event.reply("Недостаточно валюты для продажи!").setEphemeral(true).queue();
                        return;
                    }
                    double gain = price * amount * 100000;
                    String updateCurrencyQuery = "UPDATE player_currencies SET amount = amount - ? WHERE player_name = ? AND currency_id = ? AND currency_name = ?";
                    PreparedStatement updateCurrencyStmt = playersConnection.prepareStatement(updateCurrencyQuery);
                    updateCurrencyStmt.setDouble(1, amount);
                    updateCurrencyStmt.setString(2, playerName);
                    updateCurrencyStmt.setInt(3, currencyId);
                    updateCurrencyStmt.setString(4, currencyName);
                    updateCurrencyStmt.executeUpdate();
                    updateCurrencyStmt.close();
                    String updateBalanceQuery = "UPDATE player_currencies SET discord_balance = discord_balance + ? WHERE player_name = ? AND currency_name = 'None'";
                    PreparedStatement updateBalanceStmt = playersConnection.prepareStatement(updateBalanceQuery);
                    updateBalanceStmt.setDouble(1, gain);
                    updateBalanceStmt.setString(2, playerName);
                    updateBalanceStmt.executeUpdate();
                    updateBalanceStmt.close();
                    event.reply("Продано " + amount + " " + currencyName + " за " + gain + " Discord-валюты").setEphemeral(true).queue();
                } else {
                    event.reply("Действие должно быть 'buy', 'sell' или 'market'!").setEphemeral(true).queue();
                }
            }
        } catch (SQLException e) {
            event.reply("Ошибка базы данных: " + e.getMessage()).setEphemeral(true).queue();
            e.printStackTrace();
        }
    }
}
