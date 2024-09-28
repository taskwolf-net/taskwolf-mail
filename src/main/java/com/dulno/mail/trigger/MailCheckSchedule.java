package com.dulno.mail.trigger;

import com.dulno.core.CoreModule;
import com.dulno.core.iterator.AsyncIterator;
import com.dulno.core.trigger.TriggerEntry;
import com.dulno.mail.structure.MailDatabaseTable;
import com.dulno.mail.structure.MailEntry;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import lombok.RequiredArgsConstructor;

import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.search.FlagTerm;
import java.util.*;
import java.util.concurrent.*;

@RequiredArgsConstructor(staticName = "create")
public final class MailCheckSchedule {
  private final CoreModule coreModule;
  private final MailDatabaseTable mailDatabaseTable;
  private final ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1);
  private ScheduledFuture<?> scheduler;

  private static final int INBOX_CHECK_INITIAL_DELAY = 10;
  private static final int INBOX_CHECK_INTERVAL = 5 * 60;
  private static final TimeUnit INBOX_CHECK_TIME_UNIT = TimeUnit.SECONDS;

  public void start() {
    scheduler = executorService.scheduleAtFixedRate(this::execute,
      INBOX_CHECK_INITIAL_DELAY, INBOX_CHECK_INTERVAL, INBOX_CHECK_TIME_UNIT);
  }

  private void execute() {
    coreModule.findAllTriggerEntries("mail", "mail-receive-trigger")
      .thenAccept(receiveEntries -> assignTriggersToMails(receiveEntries)
        .thenAccept(this::readInboxes));
  }

  private CompletableFuture<Multimap<UUID, TriggerEntry>> assignTriggersToMails(
    List<TriggerEntry> receiveEntries
  ) {
    var entries = Lists.<TriggerEntry>newArrayList();
    entries.addAll(receiveEntries);
    var futureResponse = new CompletableFuture<Multimap<UUID, TriggerEntry>>();
    var result = HashMultimap.<UUID, TriggerEntry>create();
    AsyncIterator.execute(entries,
      entry -> coreModule.findTrigger(entry.module(), entry.type()).get()
        .findContent(entry.id()).thenAccept(content ->
          result.put(UUID.fromString((String) content.get("mailIdentifier")), entry))
        .thenAccept(value -> futureResponse.complete(result)));
    return futureResponse;
  }

  private void readInboxes(
    Multimap<UUID, TriggerEntry> entries
  ) {
    for (var mailId : entries.keySet()) {
      var mailTriggers = entries.get(mailId);
      mailDatabaseTable.findMail(mailId)
        .thenApplyAsync(this::readMailInbox)
        .thenAcceptAsync(result -> processMailTriggers(result.getKey(),
          result.getValue(), findReceivedMails(result.getValue()), mailTriggers));
    }
  }

  private Map.Entry<Store, Folder> readMailInbox(MailEntry mail) {
    try {
      var session = createSession("imap", mail.imapHost(), mail.imapPort());
      var store = session.getStore("imap");
      store.connect(mail.imapHost(), mail.mailUser(), mail.mailPassword());
      var folder = store.getFolder("INBOX");
      folder.open(Folder.READ_WRITE);
      return new AbstractMap.SimpleEntry<>(store, folder);
    } catch (Exception exception) {
      exception.printStackTrace();
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

  private static final Flags RECEIVE_FLAG = new Flags("DULNO-RECEIVE");

  private List<Message> findReceivedMails(Folder folder) {
    try {
      var receivedMails = folder.search(new FlagTerm(RECEIVE_FLAG, false));
      for (var mail : receivedMails) {
        mail.setFlags(RECEIVE_FLAG, true);
      }
      return Arrays.stream(receivedMails).toList();
    } catch (Exception exception) {
      exception.printStackTrace();
      return Lists.newArrayList();
    }
  }

  private void processMailTriggers(
    Store store, Folder folder, List<Message> receivedMails,
    Collection<TriggerEntry> triggers
  ) {
    try {
      for (var entry : triggers) {
        if (entry.type().equals("mail-receive-trigger")) {
          executeMailReceiveTrigger(entry.id(), receivedMails);
        }
      }
      folder.close(true);
      store.close();
    } catch (Exception exception) {
      exception.printStackTrace();
    }
  }

  private void executeMailReceiveTrigger(UUID triggerId, List<Message> receivedMails) {
    for (var mail : receivedMails) {
      executeMailReceiveTrigger(triggerId, mail);
    }
  }

  private void executeMailReceiveTrigger(UUID triggerId, Message mail) {
    coreModule.createWorkflow(triggerId).thenAccept(workflow ->
      workflow.trigger(createMailReceiveInformation(mail)));
  }

  private Map<String, Object> createMailReceiveInformation(Message mail) {
    try {
      var information = Maps.<String, Object>newHashMap();
      information.put("mailSender", findMailSender(mail));
      information.put("mailPrefix", findMailPrefix(mail));
      information.put("mailTitle", mail.getSubject());
      information.put("mailBody", findMailBody(mail));
      return information;
    } catch (Exception exception) {
      exception.printStackTrace();
      return Maps.newHashMap();
    }
  }

  private String findMailSender(Message mail) {
    try {
      var sender = (InternetAddress) mail.getFrom()[0];
      return sender.getAddress();
    } catch (Exception exception) {
      exception.printStackTrace();
      return "";
    }
  }

  private String findMailPrefix(Message mail) {
    try {
      var recipient = (InternetAddress) mail
        .getRecipients(Message.RecipientType.TO)[0];
      var email = recipient.getAddress();
      return email.substring(0, email.indexOf('@'));
    } catch (Exception exception) {
      exception.printStackTrace();
      return "";
    }
  }

  private String findMailBody(Message mail) throws Exception {
    if (mail.isMimeType("text/plain")) {
      return mail.getContent().toString();
    } else if (mail.isMimeType("multipart/*")) {
      var multipart = (Multipart) mail.getContent();
      var result = new StringBuilder();
      for (var i = 0; i < multipart.getCount(); i++) {
        var bodyPart = multipart.getBodyPart(i);
        if (bodyPart.isMimeType("text/plain")) {
          result.append(bodyPart.getContent());
        }
      }
      return result.toString();
    }
    return null;
  }

  public void stop() {
    scheduler.cancel(false);
  }
}
