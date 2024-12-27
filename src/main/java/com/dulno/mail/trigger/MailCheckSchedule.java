package com.dulno.mail.trigger;

import com.dulno.core.iterator.AsyncIterator;
import com.dulno.workflow.WorkflowModule;
import com.dulno.workflow.trigger.TriggerEntry;
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
  private final WorkflowModule workflowModule;
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
    workflowModule.findAllTriggerEntries("mail", "mail-receive-trigger")
      .thenAccept(receiveEntries ->
        workflowModule.findAllTriggerEntries("mail", "mail-sent-trigger")
          .thenAccept(sentEntries -> assignTriggersToMails(receiveEntries, sentEntries)
            .thenAccept(this::readInboxes)));
  }

  private CompletableFuture<Multimap<UUID, TriggerEntry>> assignTriggersToMails(
    List<TriggerEntry> receiveEntries, List<TriggerEntry> sentEntries
  ) {
    var entries = Lists.<TriggerEntry>newArrayList();
    entries.addAll(receiveEntries);
    entries.addAll(sentEntries);
    var futureResponse = new CompletableFuture<Multimap<UUID, TriggerEntry>>();
    var result = HashMultimap.<UUID, TriggerEntry>create();
    AsyncIterator.execute(entries,
      entry -> workflowModule.findTrigger(entry.module(), entry.type()).get()
        .findContent(entry.id()).thenAccept(content ->
          result.put((UUID) content.get("mailIdentifier"), entry))
        .thenAccept(value -> futureResponse.complete(result)));
    return futureResponse;
  }

  private void readInboxes(
    Multimap<UUID, TriggerEntry> entries
  ) {
    for (var mailId : entries.keySet()) {
      var mailTriggers = entries.get(mailId);
      mailDatabaseTable.findMail(mailId)
        .thenApplyAsync(this::openMailConnection)
        .thenAcceptAsync(result -> processMailTriggers(result.getKey(),
          result.getValue(), findReceivedMails(result.getValue()[0]),
          findSentMails(result.getValue()[1]), Lists.newArrayList(mailTriggers)));
    }
  }

  private Map.Entry<Store, Folder[]> openMailConnection(MailEntry mail) {
    try {
      var session = createSession("imap", mail.imapHost(), mail.imapPort());
      var store = session.getStore("imap");
      store.connect(mail.imapHost(), mail.mailUser(), mail.mailPassword());
      var inboxFolder = store.getFolder("INBOX");
      inboxFolder.open(Folder.READ_WRITE);
      var sentFolder = store.getFolder("Sent");
      sentFolder.open(Folder.READ_WRITE);
      return new AbstractMap.SimpleEntry<>(store, new Folder[] {inboxFolder,
        sentFolder});
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

  private static final Flags RECEIVE_FLAG = new Flags("DULNO-RECEIVE");

  private List<Message> findReceivedMails(Folder inboxFolder) {
    try {
      var receivedMails = inboxFolder.search(new FlagTerm(RECEIVE_FLAG, false));
      for (var mail : receivedMails) {
        mail.setFlags(RECEIVE_FLAG, true);
      }
      return Arrays.stream(receivedMails).toList();
    } catch (Exception exception) {
      return Lists.newArrayList();
    }
  }

  private static final Flags SENT_FLAG = new Flags("DULNO-SENT");

  private List<Message> findSentMails(Folder sentFolder) {
    try {
      var sentMails = sentFolder.search(new FlagTerm(SENT_FLAG, false));
      for (var mail : sentMails) {
        mail.setFlags(SENT_FLAG, true);
      }
      return Arrays.stream(sentMails).toList();
    } catch (Exception exception) {
      return Lists.newArrayList();
    }
  }

  private void processMailTriggers(
    Store store, Folder[] folders, List<Message> receivedMails,
    List<Message> sentMails, List<TriggerEntry> triggers
  ) {
    AsyncIterator.execute(triggers, trigger ->
        processMailTrigger(trigger, receivedMails, sentMails))
      .thenAccept(value -> closeMailSession(store, folders));
  }

  private void closeMailSession(Store store, Folder[] folders) {
    try {
      for (var folder : folders) {
        folder.close(true);
      }
      store.close();
    } catch (Exception exception) {
    }
  }

  private CompletableFuture<Void> processMailTrigger(
    TriggerEntry entry, List<Message> receivedMails, List<Message> sentMails
  ) {
    if (entry.type().equals("mail-receive-trigger")) {
      return AsyncIterator.execute(receivedMails, mail ->
        executeMailTrigger(entry.id(), mail)).thenApply(value -> null);
    } else if (entry.type().equals("mail-sent-trigger")) {
      return AsyncIterator.execute(sentMails, mail ->
        executeMailTrigger(entry.id(), mail)).thenApply(value -> null);
    }
    return null;
  }

  private CompletableFuture<Void> executeMailTrigger(
    UUID triggerId, Message mail
  ) {
    return workflowModule.createWorkflow(triggerId).thenAccept(workflow ->
      workflow.trigger(createMailInformation(mail)));
  }

  private Map<String, Object> createMailInformation(Message mail) {
    try {
      var information = Maps.<String, Object>newHashMap();
      information.put("mailEntryIdentifier", mail.getHeader("Message-ID")[0]);
      information.put("mailSender", findMailSender(mail));
      information.put("mailPrefix", findMailPrefix(mail));
      information.put("mailTitle", mail.getSubject());
      information.put("mailBody", findMailBody(mail));
      return information;
    } catch (Exception exception) {
      return Maps.newHashMap();
    }
  }

  private String findMailSender(Message mail) {
    try {
      var sender = (InternetAddress) mail.getFrom()[0];
      return sender.getAddress();
    } catch (Exception exception) {
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
