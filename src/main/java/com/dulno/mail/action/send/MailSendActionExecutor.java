package com.dulno.mail.action.send;

import com.dulno.workflow.action.ActionExecutor;
import com.dulno.workflow.action.ActionResult;
import com.dulno.workflow.placeholder.PlaceholderDissolve;
import com.dulno.mail.structure.MailDatabaseTable;
import com.dulno.mail.structure.MailEntry;
import com.google.common.collect.Maps;
import lombok.AllArgsConstructor;

import javax.mail.Address;
import javax.mail.Message;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@AllArgsConstructor(staticName = "create")
public final class MailSendActionExecutor implements ActionExecutor {
  private final MailDatabaseTable mailDatabaseTable;
  private final UUID ownerId;
  private final UUID mailId;
  private String mailPrefix;
  private String mailName;
  private String mailReceiver;
  private String mailTitle;
  private String mailBody;

  @Override
  public CompletableFuture<ActionResult> execute(Map<String, Object> information) {
    var dissolve = PlaceholderDissolve.create(information);
    mailPrefix = dissolve.dissolve(mailPrefix);
    mailName = dissolve.dissolve(mailName);
    mailReceiver = dissolve.dissolve(mailReceiver);
    mailTitle = dissolve.dissolve(mailTitle);
    mailBody = dissolve.dissolve(mailBody);
    return mailDatabaseTable.mailExists(mailId).thenCompose(this::execute);
  }

  private CompletableFuture<ActionResult> execute(boolean mailExists) {
    if (!mailExists) {
      return ActionResult.futureFailure("mail.action.send.failure.mail.not.found");
    }
    return mailDatabaseTable.findMail(mailId).thenApplyAsync(this::execute);
  }

  private ActionResult execute(MailEntry mail) {
    try {
      if (!mail.ownerId().equals(ownerId)) {
        return ActionResult.failure("mail.action.send.failure.mail.access");
      }
      var session = createSession("smtp", mail.smtpHost(), mail.smtpPort());
      var address = createAddress(mailReceiver);
      if (address == null) {
        return ActionResult.failure("mail.action.send.failure.mail.address");
      }
      var message = createMessage(mail, session, new Address[] {address},
        mailTitle, mailBody);
      var transport = session.getTransport("smtp");
      transport.connect(mail.smtpHost(), mail.mailUser(), mail.mailPassword());
      transport.sendMessage(message, message.getAllRecipients());
      transport.close();
      return ActionResult.success(buildInformation(mail, message.getMessageID()));
    } catch (Exception exception) {
      return ActionResult.failure(exception.getMessage());
    }
  }

  private InternetAddress createAddress(String email) {
    try {
      return new InternetAddress(email);
    } catch (Exception exception) {
      return null;
    }
  }

  private Session createSession(String protocol, String host, int port) {
    var properties = System.getProperties();
    properties.put("mail." + protocol + ".host", host);
    properties.put("mail." + protocol + ".port", port);
    properties.put("mail." + protocol + ".starttls.enable", "true");
    properties.put("mail." + protocol + ".socketFactory.class",
      "javax.net.ssl.SSLSocketFactory");
    var session = Session.getDefaultInstance(properties, null);
    session.setDebug(false);
    return session;
  }

  private MimeMessage createMessage(
    MailEntry mail, Session session, Address[] addresses, String title, String body
  ) throws Exception {
    var message = new MimeMessage(session);
    message.setFrom(new InternetAddress(mailPrefix + "@" + mail.domain(), mailName));
    message.setRecipients(Message.RecipientType.TO, addresses);
    message.setSentDate(new Date());
    message.setSubject(title);
    message.setText(body);
    return message;
  }

  private Map<String, Object> buildInformation(MailEntry mail, String entryIdentifier) {
    var information = Maps.<String, Object>newHashMap();
    information.put("mailEntryIdentifier", entryIdentifier);
    information.put("mailSender", mailPrefix + "@" + mail.domain());
    information.put("mailReceiver", mailReceiver);
    information.put("mailTitle", mailTitle);
    information.put("mailBody", mailBody);
    return information;
  }
}
