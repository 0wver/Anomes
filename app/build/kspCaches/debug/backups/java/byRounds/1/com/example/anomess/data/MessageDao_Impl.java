package com.example.anomess.data;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.room.util.StringUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Integer;
import java.lang.Long;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.StringBuilder;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlinx.coroutines.flow.Flow;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class MessageDao_Impl implements MessageDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<Message> __insertionAdapterOfMessage;

  private final SharedSQLiteStatement __preparedStmtOfMarkMessagesAsRead;

  private final SharedSQLiteStatement __preparedStmtOfMarkMessageAsReadByPeer;

  private final SharedSQLiteStatement __preparedStmtOfClearConversation;

  private final SharedSQLiteStatement __preparedStmtOfUpdateMessageStatus;

  public MessageDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfMessage = new EntityInsertionAdapter<Message>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR ABORT INTO `messages` (`id`,`senderOnionAddress`,`receiverOnionAddress`,`content`,`timestamp`,`isMine`,`isRead`,`type`,`mediaPath`,`senderTimestamp`,`status`,`replyToMessageId`,`replyToContent`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final Message entity) {
        statement.bindLong(1, entity.getId());
        statement.bindString(2, entity.getSenderOnionAddress());
        statement.bindString(3, entity.getReceiverOnionAddress());
        statement.bindString(4, entity.getContent());
        statement.bindLong(5, entity.getTimestamp());
        final int _tmp = entity.isMine() ? 1 : 0;
        statement.bindLong(6, _tmp);
        final int _tmp_1 = entity.isRead() ? 1 : 0;
        statement.bindLong(7, _tmp_1);
        statement.bindLong(8, entity.getType());
        if (entity.getMediaPath() == null) {
          statement.bindNull(9);
        } else {
          statement.bindString(9, entity.getMediaPath());
        }
        if (entity.getSenderTimestamp() == null) {
          statement.bindNull(10);
        } else {
          statement.bindLong(10, entity.getSenderTimestamp());
        }
        statement.bindLong(11, entity.getStatus());
        if (entity.getReplyToMessageId() == null) {
          statement.bindNull(12);
        } else {
          statement.bindLong(12, entity.getReplyToMessageId());
        }
        if (entity.getReplyToContent() == null) {
          statement.bindNull(13);
        } else {
          statement.bindString(13, entity.getReplyToContent());
        }
      }
    };
    this.__preparedStmtOfMarkMessagesAsRead = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE messages SET isRead = 1 WHERE senderOnionAddress = ? AND isRead = 0";
        return _query;
      }
    };
    this.__preparedStmtOfMarkMessageAsReadByPeer = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE messages SET isRead = 1 WHERE timestamp <= ? AND isMine = 1 AND isRead = 0";
        return _query;
      }
    };
    this.__preparedStmtOfClearConversation = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM messages WHERE senderOnionAddress = ? OR receiverOnionAddress = ?";
        return _query;
      }
    };
    this.__preparedStmtOfUpdateMessageStatus = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE messages SET status = ? WHERE id = ?";
        return _query;
      }
    };
  }

  @Override
  public Object insertMessage(final Message message, final Continuation<? super Long> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Long>() {
      @Override
      @NonNull
      public Long call() throws Exception {
        __db.beginTransaction();
        try {
          final Long _result = __insertionAdapterOfMessage.insertAndReturnId(message);
          __db.setTransactionSuccessful();
          return _result;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object markMessagesAsRead(final String contactAddress,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfMarkMessagesAsRead.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, contactAddress);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfMarkMessagesAsRead.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object markMessageAsReadByPeer(final long timestamp,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfMarkMessageAsReadByPeer.acquire();
        int _argIndex = 1;
        _stmt.bindLong(_argIndex, timestamp);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfMarkMessageAsReadByPeer.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object clearConversation(final String address,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfClearConversation.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, address);
        _argIndex = 2;
        _stmt.bindString(_argIndex, address);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfClearConversation.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object updateMessageStatus(final int id, final int status,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfUpdateMessageStatus.acquire();
        int _argIndex = 1;
        _stmt.bindLong(_argIndex, status);
        _argIndex = 2;
        _stmt.bindLong(_argIndex, id);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfUpdateMessageStatus.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<Message>> getMessagesForConversation(final String address) {
    final String _sql = "SELECT * FROM messages WHERE senderOnionAddress = ? OR receiverOnionAddress = ? ORDER BY timestamp ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 2);
    int _argIndex = 1;
    _statement.bindString(_argIndex, address);
    _argIndex = 2;
    _statement.bindString(_argIndex, address);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"messages"}, new Callable<List<Message>>() {
      @Override
      @NonNull
      public List<Message> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfSenderOnionAddress = CursorUtil.getColumnIndexOrThrow(_cursor, "senderOnionAddress");
          final int _cursorIndexOfReceiverOnionAddress = CursorUtil.getColumnIndexOrThrow(_cursor, "receiverOnionAddress");
          final int _cursorIndexOfContent = CursorUtil.getColumnIndexOrThrow(_cursor, "content");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final int _cursorIndexOfIsMine = CursorUtil.getColumnIndexOrThrow(_cursor, "isMine");
          final int _cursorIndexOfIsRead = CursorUtil.getColumnIndexOrThrow(_cursor, "isRead");
          final int _cursorIndexOfType = CursorUtil.getColumnIndexOrThrow(_cursor, "type");
          final int _cursorIndexOfMediaPath = CursorUtil.getColumnIndexOrThrow(_cursor, "mediaPath");
          final int _cursorIndexOfSenderTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "senderTimestamp");
          final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
          final int _cursorIndexOfReplyToMessageId = CursorUtil.getColumnIndexOrThrow(_cursor, "replyToMessageId");
          final int _cursorIndexOfReplyToContent = CursorUtil.getColumnIndexOrThrow(_cursor, "replyToContent");
          final List<Message> _result = new ArrayList<Message>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final Message _item;
            final int _tmpId;
            _tmpId = _cursor.getInt(_cursorIndexOfId);
            final String _tmpSenderOnionAddress;
            _tmpSenderOnionAddress = _cursor.getString(_cursorIndexOfSenderOnionAddress);
            final String _tmpReceiverOnionAddress;
            _tmpReceiverOnionAddress = _cursor.getString(_cursorIndexOfReceiverOnionAddress);
            final String _tmpContent;
            _tmpContent = _cursor.getString(_cursorIndexOfContent);
            final long _tmpTimestamp;
            _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp);
            final boolean _tmpIsMine;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsMine);
            _tmpIsMine = _tmp != 0;
            final boolean _tmpIsRead;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfIsRead);
            _tmpIsRead = _tmp_1 != 0;
            final int _tmpType;
            _tmpType = _cursor.getInt(_cursorIndexOfType);
            final String _tmpMediaPath;
            if (_cursor.isNull(_cursorIndexOfMediaPath)) {
              _tmpMediaPath = null;
            } else {
              _tmpMediaPath = _cursor.getString(_cursorIndexOfMediaPath);
            }
            final Long _tmpSenderTimestamp;
            if (_cursor.isNull(_cursorIndexOfSenderTimestamp)) {
              _tmpSenderTimestamp = null;
            } else {
              _tmpSenderTimestamp = _cursor.getLong(_cursorIndexOfSenderTimestamp);
            }
            final int _tmpStatus;
            _tmpStatus = _cursor.getInt(_cursorIndexOfStatus);
            final Integer _tmpReplyToMessageId;
            if (_cursor.isNull(_cursorIndexOfReplyToMessageId)) {
              _tmpReplyToMessageId = null;
            } else {
              _tmpReplyToMessageId = _cursor.getInt(_cursorIndexOfReplyToMessageId);
            }
            final String _tmpReplyToContent;
            if (_cursor.isNull(_cursorIndexOfReplyToContent)) {
              _tmpReplyToContent = null;
            } else {
              _tmpReplyToContent = _cursor.getString(_cursorIndexOfReplyToContent);
            }
            _item = new Message(_tmpId,_tmpSenderOnionAddress,_tmpReceiverOnionAddress,_tmpContent,_tmpTimestamp,_tmpIsMine,_tmpIsRead,_tmpType,_tmpMediaPath,_tmpSenderTimestamp,_tmpStatus,_tmpReplyToMessageId,_tmpReplyToContent);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Flow<Message> getLastMessage(final String address) {
    final String _sql = "SELECT * FROM messages WHERE senderOnionAddress = ? OR receiverOnionAddress = ? ORDER BY timestamp DESC LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 2);
    int _argIndex = 1;
    _statement.bindString(_argIndex, address);
    _argIndex = 2;
    _statement.bindString(_argIndex, address);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"messages"}, new Callable<Message>() {
      @Override
      @Nullable
      public Message call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfSenderOnionAddress = CursorUtil.getColumnIndexOrThrow(_cursor, "senderOnionAddress");
          final int _cursorIndexOfReceiverOnionAddress = CursorUtil.getColumnIndexOrThrow(_cursor, "receiverOnionAddress");
          final int _cursorIndexOfContent = CursorUtil.getColumnIndexOrThrow(_cursor, "content");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final int _cursorIndexOfIsMine = CursorUtil.getColumnIndexOrThrow(_cursor, "isMine");
          final int _cursorIndexOfIsRead = CursorUtil.getColumnIndexOrThrow(_cursor, "isRead");
          final int _cursorIndexOfType = CursorUtil.getColumnIndexOrThrow(_cursor, "type");
          final int _cursorIndexOfMediaPath = CursorUtil.getColumnIndexOrThrow(_cursor, "mediaPath");
          final int _cursorIndexOfSenderTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "senderTimestamp");
          final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
          final int _cursorIndexOfReplyToMessageId = CursorUtil.getColumnIndexOrThrow(_cursor, "replyToMessageId");
          final int _cursorIndexOfReplyToContent = CursorUtil.getColumnIndexOrThrow(_cursor, "replyToContent");
          final Message _result;
          if (_cursor.moveToFirst()) {
            final int _tmpId;
            _tmpId = _cursor.getInt(_cursorIndexOfId);
            final String _tmpSenderOnionAddress;
            _tmpSenderOnionAddress = _cursor.getString(_cursorIndexOfSenderOnionAddress);
            final String _tmpReceiverOnionAddress;
            _tmpReceiverOnionAddress = _cursor.getString(_cursorIndexOfReceiverOnionAddress);
            final String _tmpContent;
            _tmpContent = _cursor.getString(_cursorIndexOfContent);
            final long _tmpTimestamp;
            _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp);
            final boolean _tmpIsMine;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsMine);
            _tmpIsMine = _tmp != 0;
            final boolean _tmpIsRead;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfIsRead);
            _tmpIsRead = _tmp_1 != 0;
            final int _tmpType;
            _tmpType = _cursor.getInt(_cursorIndexOfType);
            final String _tmpMediaPath;
            if (_cursor.isNull(_cursorIndexOfMediaPath)) {
              _tmpMediaPath = null;
            } else {
              _tmpMediaPath = _cursor.getString(_cursorIndexOfMediaPath);
            }
            final Long _tmpSenderTimestamp;
            if (_cursor.isNull(_cursorIndexOfSenderTimestamp)) {
              _tmpSenderTimestamp = null;
            } else {
              _tmpSenderTimestamp = _cursor.getLong(_cursorIndexOfSenderTimestamp);
            }
            final int _tmpStatus;
            _tmpStatus = _cursor.getInt(_cursorIndexOfStatus);
            final Integer _tmpReplyToMessageId;
            if (_cursor.isNull(_cursorIndexOfReplyToMessageId)) {
              _tmpReplyToMessageId = null;
            } else {
              _tmpReplyToMessageId = _cursor.getInt(_cursorIndexOfReplyToMessageId);
            }
            final String _tmpReplyToContent;
            if (_cursor.isNull(_cursorIndexOfReplyToContent)) {
              _tmpReplyToContent = null;
            } else {
              _tmpReplyToContent = _cursor.getString(_cursorIndexOfReplyToContent);
            }
            _result = new Message(_tmpId,_tmpSenderOnionAddress,_tmpReceiverOnionAddress,_tmpContent,_tmpTimestamp,_tmpIsMine,_tmpIsRead,_tmpType,_tmpMediaPath,_tmpSenderTimestamp,_tmpStatus,_tmpReplyToMessageId,_tmpReplyToContent);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Flow<List<Message>> getAllMessages() {
    final String _sql = "SELECT * FROM messages ORDER BY timestamp DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"messages"}, new Callable<List<Message>>() {
      @Override
      @NonNull
      public List<Message> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfSenderOnionAddress = CursorUtil.getColumnIndexOrThrow(_cursor, "senderOnionAddress");
          final int _cursorIndexOfReceiverOnionAddress = CursorUtil.getColumnIndexOrThrow(_cursor, "receiverOnionAddress");
          final int _cursorIndexOfContent = CursorUtil.getColumnIndexOrThrow(_cursor, "content");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final int _cursorIndexOfIsMine = CursorUtil.getColumnIndexOrThrow(_cursor, "isMine");
          final int _cursorIndexOfIsRead = CursorUtil.getColumnIndexOrThrow(_cursor, "isRead");
          final int _cursorIndexOfType = CursorUtil.getColumnIndexOrThrow(_cursor, "type");
          final int _cursorIndexOfMediaPath = CursorUtil.getColumnIndexOrThrow(_cursor, "mediaPath");
          final int _cursorIndexOfSenderTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "senderTimestamp");
          final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
          final int _cursorIndexOfReplyToMessageId = CursorUtil.getColumnIndexOrThrow(_cursor, "replyToMessageId");
          final int _cursorIndexOfReplyToContent = CursorUtil.getColumnIndexOrThrow(_cursor, "replyToContent");
          final List<Message> _result = new ArrayList<Message>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final Message _item;
            final int _tmpId;
            _tmpId = _cursor.getInt(_cursorIndexOfId);
            final String _tmpSenderOnionAddress;
            _tmpSenderOnionAddress = _cursor.getString(_cursorIndexOfSenderOnionAddress);
            final String _tmpReceiverOnionAddress;
            _tmpReceiverOnionAddress = _cursor.getString(_cursorIndexOfReceiverOnionAddress);
            final String _tmpContent;
            _tmpContent = _cursor.getString(_cursorIndexOfContent);
            final long _tmpTimestamp;
            _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp);
            final boolean _tmpIsMine;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsMine);
            _tmpIsMine = _tmp != 0;
            final boolean _tmpIsRead;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfIsRead);
            _tmpIsRead = _tmp_1 != 0;
            final int _tmpType;
            _tmpType = _cursor.getInt(_cursorIndexOfType);
            final String _tmpMediaPath;
            if (_cursor.isNull(_cursorIndexOfMediaPath)) {
              _tmpMediaPath = null;
            } else {
              _tmpMediaPath = _cursor.getString(_cursorIndexOfMediaPath);
            }
            final Long _tmpSenderTimestamp;
            if (_cursor.isNull(_cursorIndexOfSenderTimestamp)) {
              _tmpSenderTimestamp = null;
            } else {
              _tmpSenderTimestamp = _cursor.getLong(_cursorIndexOfSenderTimestamp);
            }
            final int _tmpStatus;
            _tmpStatus = _cursor.getInt(_cursorIndexOfStatus);
            final Integer _tmpReplyToMessageId;
            if (_cursor.isNull(_cursorIndexOfReplyToMessageId)) {
              _tmpReplyToMessageId = null;
            } else {
              _tmpReplyToMessageId = _cursor.getInt(_cursorIndexOfReplyToMessageId);
            }
            final String _tmpReplyToContent;
            if (_cursor.isNull(_cursorIndexOfReplyToContent)) {
              _tmpReplyToContent = null;
            } else {
              _tmpReplyToContent = _cursor.getString(_cursorIndexOfReplyToContent);
            }
            _item = new Message(_tmpId,_tmpSenderOnionAddress,_tmpReceiverOnionAddress,_tmpContent,_tmpTimestamp,_tmpIsMine,_tmpIsRead,_tmpType,_tmpMediaPath,_tmpSenderTimestamp,_tmpStatus,_tmpReplyToMessageId,_tmpReplyToContent);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Flow<Integer> getUnreadCount(final String contactAddress) {
    final String _sql = "SELECT COUNT(*) FROM messages WHERE senderOnionAddress = ? AND isRead = 0";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, contactAddress);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"messages"}, new Callable<Integer>() {
      @Override
      @NonNull
      public Integer call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Integer _result;
          if (_cursor.moveToFirst()) {
            final int _tmp;
            _tmp = _cursor.getInt(0);
            _result = _tmp;
          } else {
            _result = 0;
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Object getPendingMessages(final Continuation<? super List<Message>> $completion) {
    final String _sql = "SELECT * FROM messages WHERE status = 2 ORDER BY timestamp ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<Message>>() {
      @Override
      @NonNull
      public List<Message> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfSenderOnionAddress = CursorUtil.getColumnIndexOrThrow(_cursor, "senderOnionAddress");
          final int _cursorIndexOfReceiverOnionAddress = CursorUtil.getColumnIndexOrThrow(_cursor, "receiverOnionAddress");
          final int _cursorIndexOfContent = CursorUtil.getColumnIndexOrThrow(_cursor, "content");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final int _cursorIndexOfIsMine = CursorUtil.getColumnIndexOrThrow(_cursor, "isMine");
          final int _cursorIndexOfIsRead = CursorUtil.getColumnIndexOrThrow(_cursor, "isRead");
          final int _cursorIndexOfType = CursorUtil.getColumnIndexOrThrow(_cursor, "type");
          final int _cursorIndexOfMediaPath = CursorUtil.getColumnIndexOrThrow(_cursor, "mediaPath");
          final int _cursorIndexOfSenderTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "senderTimestamp");
          final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
          final int _cursorIndexOfReplyToMessageId = CursorUtil.getColumnIndexOrThrow(_cursor, "replyToMessageId");
          final int _cursorIndexOfReplyToContent = CursorUtil.getColumnIndexOrThrow(_cursor, "replyToContent");
          final List<Message> _result = new ArrayList<Message>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final Message _item;
            final int _tmpId;
            _tmpId = _cursor.getInt(_cursorIndexOfId);
            final String _tmpSenderOnionAddress;
            _tmpSenderOnionAddress = _cursor.getString(_cursorIndexOfSenderOnionAddress);
            final String _tmpReceiverOnionAddress;
            _tmpReceiverOnionAddress = _cursor.getString(_cursorIndexOfReceiverOnionAddress);
            final String _tmpContent;
            _tmpContent = _cursor.getString(_cursorIndexOfContent);
            final long _tmpTimestamp;
            _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp);
            final boolean _tmpIsMine;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsMine);
            _tmpIsMine = _tmp != 0;
            final boolean _tmpIsRead;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfIsRead);
            _tmpIsRead = _tmp_1 != 0;
            final int _tmpType;
            _tmpType = _cursor.getInt(_cursorIndexOfType);
            final String _tmpMediaPath;
            if (_cursor.isNull(_cursorIndexOfMediaPath)) {
              _tmpMediaPath = null;
            } else {
              _tmpMediaPath = _cursor.getString(_cursorIndexOfMediaPath);
            }
            final Long _tmpSenderTimestamp;
            if (_cursor.isNull(_cursorIndexOfSenderTimestamp)) {
              _tmpSenderTimestamp = null;
            } else {
              _tmpSenderTimestamp = _cursor.getLong(_cursorIndexOfSenderTimestamp);
            }
            final int _tmpStatus;
            _tmpStatus = _cursor.getInt(_cursorIndexOfStatus);
            final Integer _tmpReplyToMessageId;
            if (_cursor.isNull(_cursorIndexOfReplyToMessageId)) {
              _tmpReplyToMessageId = null;
            } else {
              _tmpReplyToMessageId = _cursor.getInt(_cursorIndexOfReplyToMessageId);
            }
            final String _tmpReplyToContent;
            if (_cursor.isNull(_cursorIndexOfReplyToContent)) {
              _tmpReplyToContent = null;
            } else {
              _tmpReplyToContent = _cursor.getString(_cursorIndexOfReplyToContent);
            }
            _item = new Message(_tmpId,_tmpSenderOnionAddress,_tmpReceiverOnionAddress,_tmpContent,_tmpTimestamp,_tmpIsMine,_tmpIsRead,_tmpType,_tmpMediaPath,_tmpSenderTimestamp,_tmpStatus,_tmpReplyToMessageId,_tmpReplyToContent);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object findMessageId(final String senderAddress, final long timestamp,
      final Continuation<? super Integer> $completion) {
    final String _sql = "\n"
            + "        SELECT id FROM messages \n"
            + "        WHERE senderOnionAddress = ? \n"
            + "        AND (\n"
            + "            (isMine = 1 AND timestamp = ?)\n"
            + "            OR \n"
            + "            (isMine = 0 AND senderTimestamp = ?)\n"
            + "        )\n"
            + "        LIMIT 1\n"
            + "    ";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 3);
    int _argIndex = 1;
    _statement.bindString(_argIndex, senderAddress);
    _argIndex = 2;
    _statement.bindLong(_argIndex, timestamp);
    _argIndex = 3;
    _statement.bindLong(_argIndex, timestamp);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<Integer>() {
      @Override
      @Nullable
      public Integer call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Integer _result;
          if (_cursor.moveToFirst()) {
            if (_cursor.isNull(0)) {
              _result = null;
            } else {
              _result = _cursor.getInt(0);
            }
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteMessages(final List<Integer> ids,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final StringBuilder _stringBuilder = StringUtil.newStringBuilder();
        _stringBuilder.append("DELETE FROM messages WHERE id IN (");
        final int _inputSize = ids.size();
        StringUtil.appendPlaceholders(_stringBuilder, _inputSize);
        _stringBuilder.append(")");
        final String _sql = _stringBuilder.toString();
        final SupportSQLiteStatement _stmt = __db.compileStatement(_sql);
        int _argIndex = 1;
        for (int _item : ids) {
          _stmt.bindLong(_argIndex, _item);
          _argIndex++;
        }
        __db.beginTransaction();
        try {
          _stmt.executeUpdateDelete();
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
