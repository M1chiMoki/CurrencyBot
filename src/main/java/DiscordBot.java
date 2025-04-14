import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.GatewayIntent;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class DiscordBot extends ListenerAdapter {

    private final Connection economicConnection;
    private final Connection playersConnection;

    public DiscordBot() throws SQLException {
        String economicUrl = "jdbc:mysql://uran.minerent.net:3306/s83687_economic";
        String economicUser = "u83687_CBVu9IOxUY";
        String economicPassword = "RXybOfBVvRm=JViQJB@Vcr7Z";
        economicConnection = DriverManager.getConnection(economicUrl, economicUser, economicPassword);

        String playersUrl = "jdbc:mysql://uran.minerent.net:3306/s83687_economic_players";
        String playersUser = "u83687_wxvlsshO2A";
        String playersPassword = "Yd!.Ao^7TYmfiNaNUr^dtCli";
        playersConnection = DriverManager.getConnection(playersUrl, playersUser, playersPassword);
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
                Commands.slash("account", "Link your Minecraft account")
                        .addOption(OptionType.STRING, "player", "Minecraft username", true)
                        .addOption(OptionType.STRING, "password", "Crypto password", true),
                Commands.slash("diseco", "Admin Discord currency commands")
                        .addOption(OptionType.USER, "user", "Target user", true)
                        .addOption(OptionType.STRING, "action", "give or take", true)
                        .addOption(OptionType.NUMBER, "amount", "Amount", true),
                Commands.slash("economic", "Economy commands")
                        .addOption(OptionType.STRING, "action", "buy or sell", true)
                        .addOption(OptionType.STRING, "currency", "Currency name or ID", true)
                        .addOption(OptionType.NUMBER, "amount", "Amount", true)
        ).queue();
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        String command = event.getName();
        User user = event.getUser();
        String discordId = user.getId();
        String discordName = user.getAsTag();

        try {
            if (command.equals("account")) {
                String playerName = event.getOption("player").getAsString();
                String password = event.getOption("password").getAsString();
                String query = "SELECT crypto_password FROM player_currencies WHERE player_name = ? LIMIT 1";
                PreparedStatement stmt = playersConnection.prepareStatement(query);
                stmt.setString(1, playerName);
                ResultSet rs = stmt.executeQuery();
                if (!rs.next()) {
                    event.reply("Игрок " + playerName + " не найден!").setEphemeral(true).queue();
                    rs.close();
                    stmt.close();
                    return;
                }
                String storedPassword = rs.getString("crypto_password");
                rs.close();
                stmt.close();
                if (!password.equals(storedPassword)) {
                    event.reply("Неверный крипто-пароль!").setEphemeral(true).queue();
                    return;
                }
                String updateQuery = "UPDATE player_currencies SET discord_id = ?, discord_name = ? WHERE player_name = ?";
                PreparedStatement updateStmt = playersConnection.prepareStatement(updateQuery);
                updateStmt.setString(1, discordId);
                updateStmt.setString(2, discordName);
                updateStmt.setString(3, playerName);
                updateStmt.executeUpdate();
                updateStmt.close();
                event.reply("Аккаунт успешно привязан!").setEphemeral(true).queue();
                return;
            }

            // Проверка привязки аккаунта
            String playerName = null;
            String query = "SELECT player_name FROM player_currencies WHERE discord_id = ? LIMIT 1";
            PreparedStatement stmt = playersConnection.prepareStatement(query);
            stmt.setString(1, discordId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                playerName = rs.getString("player_name");
            }
            rs.close();
            stmt.close();
            if (playerName == null && !command.equals("diseco")) {
                event.reply("Вы не привязали аккаунт! Используйте /account link [ник] [крипто-пароль]").setEphemeral(true).queue();
                return;
            }

            if (command.equals("balance")) {
                String balanceQuery = "SELECT currency_name, amount FROM player_currencies WHERE player_name = ?";
                PreparedStatement balanceStmt = playersConnection.prepareStatement(balanceQuery);
                balanceStmt.setString(1, playerName);
                ResultSet balanceRs = balanceStmt.executeQuery();
                StringBuilder response = new StringBuilder("Ваш баланс:\n");
                while (balanceRs.next()) {
                    response.append(balanceRs.getString("currency_name")).append(": ").append(String.format("%.2f", balanceRs.getDouble("amount"))).append("\n");
                }
                balanceRs.close();
                balanceStmt.close();
                event.reply(response.toString()).setEphemeral(true).queue();
                return;
            }

            if (command.equals("disbalance")) {
                String balanceQuery = "SELECT discord_balance FROM player_currencies WHERE player_name = ? LIMIT 1";
                PreparedStatement balanceStmt = playersConnection.prepareStatement(balanceQuery);
                balanceStmt.setString(1, playerName);
                ResultSet balanceRs = balanceStmt.executeQuery();
                double discordBalance = balanceRs.next() ? balanceRs.getDouble("discord_balance") : 0;
                balanceRs.close();
                balanceStmt.close();
                event.reply("Ваш Discord-баланс: " + String.format("%.2f", discordBalance)).setEphemeral(true).queue();
                return;
            }

            if (command.equals("diseco")) {
                // Проверка админ-прав (например, по роли)
                boolean isAdmin = event.getMember().getRoles().stream().anyMatch(role -> role.getName().equals("Admin")); // Настройте роль
                if (!isAdmin) {
                    event.reply("У вас нет прав администратора!").setEphemeral(true).queue();
                    return;
                }
                User targetUser = event.getOption("user").getAsUser();
                String action = event.getOption("action").getAsString();
                double amount = event.getOption("amount").getAsDouble();
                if (amount <= 0) {
                    event.reply("Количество должно быть положительным!").setEphemeral(true).queue();
                    return;
                }
                String targetQuery = "SELECT player_name FROM player_currencies WHERE discord_id = ? LIMIT 1";
                PreparedStatement targetStmt = playersConnection.prepareStatement(targetQuery);
                targetStmt.setString(1, targetUser.getId());
                ResultSet targetRs = targetStmt.executeQuery();
                if (!targetRs.next()) {
                    event.reply("Пользователь не привязал аккаунт!").setEphemeral(true).queue();
                    targetRs.close();
                    targetStmt.close();
                    return;
                }
                String targetPlayer = targetRs.getString("player_name");
                targetRs.close();
                targetStmt.close();
                if (action.equalsIgnoreCase("give")) {
                    String updateQuery = "UPDATE player_currencies SET discord_balance = discord_balance + ? WHERE player_name = ?";
                    PreparedStatement updateStmt = playersConnection.prepareStatement(updateQuery);
                    updateStmt.setDouble(1, amount);
                    updateStmt.setString(2, targetPlayer);
                    updateStmt.executeUpdate();
                    updateStmt.close();
                    event.reply("Выдано " + amount + " Discord-валюты пользователю " + targetUser.getAsTag()).setEphemeral(true).queue();
                } else if (action.equalsIgnoreCase("take")) {
                    String updateQuery = "UPDATE player_currencies SET discord_balance = GREATEST(0, discord_balance - ?) WHERE player_name = ?";
                    PreparedStatement updateStmt = playersConnection.prepareStatement(updateQuery);
                    updateStmt.setDouble(1, amount);
                    updateStmt.setString(2, targetPlayer);
                    updateStmt.executeUpdate();
                    updateStmt.close();
                    event.reply("Забрано " + amount + " Discord-валюты у пользователя " + targetUser.getAsTag()).setEphemeral(true).queue();
                } else {
                    event.reply("Действие должно быть 'give' или 'take'!").setEphemeral(true).queue();
                }
                return;
            }

            if (command.equals("economic")) {
                String action = event.getOption("action").getAsString().toLowerCase();
                String currencyInput = event.getOption("currency").getAsString();
                double amount = event.getOption("amount").getAsDouble();
                if (amount <= 0) {
                    event.reply("Количество должно быть положительным!").setEphemeral(true).queue();
                    return;
                }
                String currencyQuery = currencyInput.matches("\\d+") ?
                        "SELECT id, name, current_price FROM currencies WHERE id = ?" :
                        "SELECT id, name, current_price FROM currencies WHERE name = ?";
                PreparedStatement currencyStmt = economicConnection.prepareStatement(currencyQuery);
                if (currencyInput.matches("\\d+")) {
                    currencyStmt.setInt(1, Integer.parseInt(currencyInput));
                } else {
                    currencyStmt.setString(1, currencyInput);
                }
                ResultSet currencyRs = currencyStmt.executeQuery();
                if (!currencyRs.next()) {
                    event.reply("Валюта не найдена!").setEphemeral(true).queue();
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
                    String balanceQuery = "SELECT discord_balance FROM player_currencies WHERE player_name = ? LIMIT 1";
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
                    String updateBalanceQuery = "UPDATE player_currencies SET discord_balance = discord_balance - ? WHERE player_name = ?";
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
                    String balanceQuery = "SELECT amount FROM player_currencies WHERE player_name = ? AND currency_id = ?";
                    PreparedStatement balanceStmt = playersConnection.prepareStatement(balanceQuery);
                    balanceStmt.setString(1, playerName);
                    balanceStmt.setInt(2, currencyId);
                    ResultSet balanceRs = balanceStmt.executeQuery();
                    double currentAmount = balanceRs.next() ? balanceRs.getDouble("amount") : 0;
                    balanceRs.close();
                    balanceStmt.close();
                    if (currentAmount < amount) {
                        event.reply("Недостаточно валюты для продажи!").setEphemeral(true).queue();
                        return;
                    }
                    double gain = price * amount * 100000;
                    String updateCurrencyQuery = "UPDATE player_currencies SET amount = amount - ? WHERE player_name = ? AND currency_id = ?";
                    PreparedStatement updateCurrencyStmt = playersConnection.prepareStatement(updateCurrencyQuery);
                    updateCurrencyStmt.setDouble(1, amount);
                    updateCurrencyStmt.setString(2, playerName);
                    updateCurrencyStmt.setInt(3, currencyId);
                    updateCurrencyStmt.executeUpdate();
                    updateCurrencyStmt.close();
                    String updateBalanceQuery = "UPDATE player_currencies SET discord_balance = discord_balance + ? WHERE player_name = ?";
                    PreparedStatement updateBalanceStmt = playersConnection.prepareStatement(updateBalanceQuery);
                    updateBalanceStmt.setDouble(1, gain);
                    updateBalanceStmt.setString(2, playerName);
                    updateBalanceStmt.executeUpdate();
                    updateBalanceStmt.close();
                    event.reply("Продано " + amount + " " + currencyName + " за " + gain + " Discord-валюты").setEphemeral(true).queue();
                } else {
                    event.reply("Действие должно быть 'buy' или 'sell'!").setEphemeral(true).queue();
                }
            }
        } catch (SQLException e) {
            event.reply("Ошибка базы данных: " + e.getMessage()).setEphemeral(true).queue();
        }
    }
}
