package com.dulno.mail.structure;

import com.dulno.core.database.DatabaseRow;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.util.UUID;

@Getter
@Accessors(fluent = true)
@AllArgsConstructor(staticName = "create")
public final class MailEntry {
  public static MailEntry of(DatabaseRow row) {
    return create(row.findCell(0).uuidValue(), row.findCell(1).uuidValue(),
      row.findCell(2).stringValue(), row.findCell(3).integerValue(),
      row.findCell(4).integerValue());
  }

  private final UUID id;
  private final UUID ownerId;
  private final String domain;
  private final int smtpPort;
  private final int imapPort;
}
