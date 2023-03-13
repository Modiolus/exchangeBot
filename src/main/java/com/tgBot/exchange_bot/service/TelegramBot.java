package com.tgBot.exchange_bot.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tgBot.exchange_bot.config.BotConfig;
import com.tgBot.exchange_bot.model.User;
import com.tgBot.exchange_bot.repositories.UserRepository;
import com.vdurmont.emoji.EmojiParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import javax.persistence.EntityManager;
import java.sql.Timestamp;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

@Component
public class TelegramBot extends TelegramLongPollingBot {

    static final String ERROR_TEXT = "Error occurred: ";
    private static final Logger loggerInfo = LoggerFactory.getLogger("logger.info");
    private static final Logger loggerWarn = LoggerFactory.getLogger("logger. warn");

    private static final Logger loggerError = LoggerFactory.getLogger("logger error");


    private UserRepository userRepository;
    private BotConfig config;
    private EntityManager entityManager;

    @Autowired
    public TelegramBot(UserRepository userRepository, EntityManager entityManager, BotConfig config) {
        this.userRepository = userRepository;
        this.entityManager = entityManager;
        this.config = config;
    }


    @Override
    public String getBotUsername() {
        return config.getBotName();
    }

    @Override
    public String getBotToken() {
        return config.getToken();
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {

            // TODO: 11.03.2023 оставил пока здесь на преспективу запроса телефона у пользователя
//           Contact contact = update.getMessage().getContact();
//          String phoneNumber = contact.getPhoneNumber();
            String messageText = update.getMessage().getText();
            long chatId = update.getMessage().getChatId();
            if (messageText.contains("/send") && config.getOwnerId() == chatId) {
                var textToSend = EmojiParser.parseToUnicode(messageText);
                var users = userRepository.findAll();
                for (User user : users) {
                    prepareAndSendMessage(user.getChatId(), textToSend);


                }

            }
            else {
                switch (messageText) {
                    case "\uD83D\uDCB8 Курс валют":
                        currencyExchange(chatId);
                        break;

                    case "/start":
                        registerUser(update.getMessage());
                        startCommandReceived(chatId, update.getMessage().getChat().getFirstName());
                        break;

                    default:
                        prepareAndSendMessage(chatId, "Sorry, command was not recognized");
                        loggerWarn.warn("User: " + update.getMessage().getChat().getFirstName() + " has entered a wrong command");


                }
            }

        }

    }

    private void sendMessage(long chatId, String textToSend) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(textToSend);

        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        keyboardMarkup.setResizeKeyboard(true);

        List<KeyboardRow> keyboardRows = new ArrayList<>();
        KeyboardRow row = new KeyboardRow();

        row.add("\uD83D\uDCB8 Курс валют");
//        row.add("Скарги");
        row.add("Побажання");

        keyboardRows.add(row);
        keyboardMarkup.setKeyboard(keyboardRows);
        message.setReplyMarkup(keyboardMarkup);
        executeMessage(message);

    }

    private void executeMessage(SendMessage message) {
        try {
            execute(message);
        } catch (TelegramApiException e) {
            loggerError.error(ERROR_TEXT + e.getMessage());
        }
    }

    private void prepareAndSendMessage(long chatId, String textToSend) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(textToSend);
        executeMessage(message);
    }


    private void startCommandReceived(long chatId, String name) {
        String answer = EmojiParser.parseToUnicode("Hi, " + name + ", nice to meet you!" + " :blush:");
        loggerInfo.info("Replied to user " + name);
        sendMessage(chatId, answer);

    }

    public void currencyExchange(long chatId) {
        RestTemplate restTemplate = new RestTemplate();

        String urlExchange = "https://api.privatbank.ua/p24api/pubinfo?json&exchange&coursid=5";
        String res = restTemplate.getForObject(urlExchange, String.class);
        ObjectMapper mapper = new ObjectMapper();
        try {
            JsonNode obj = mapper.readTree(res);
            DecimalFormat df = new DecimalFormat("0.00");
            String buy = df.format(obj.get(1).get("buy").asDouble());
            String sale = df.format(obj.get(1).get("sale").asDouble());

            System.out.println("Курс грн за 1 $ : " + "\n" + "Покупка: " + buy + "\n" + "Продажа: " + sale);
//            long chatId, String textToSend
//            prepareAndSendMessage(chatId, );

            SendMessage message = new SendMessage();
            message.setChatId(String.valueOf(chatId));
            message.setText("Курс готівкового $ на сьогодні:\n\uD83C\uDFE6Privat Bank:\nКупівля: " + buy + " грн. за 1 $\n" + "Продаж: " + sale + " грн. за 1 $");
            executeMessage(message);


        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }


    }

    private void registerUser(Message msg) {
        if (userRepository.findById(msg.getChatId()).isEmpty()) {
            User user = new User();
            user.setChatId(msg.getChatId());
            user.setFirstName(msg.getChat().getFirstName());
            user.setLastName(msg.getChat().getLastName());
            user.setUserName(msg.getChat().getLastName());
            user.setRegisteredAt(new Timestamp(System.currentTimeMillis()));

            userRepository.save(user);

            loggerInfo.info("user saved: " + user);

        }

    }
}
