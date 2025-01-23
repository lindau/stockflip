package com.stockflip;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityDeletionOrUpdateAdapter;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class StockPairDao_Impl implements StockPairDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<StockPair> __insertionAdapterOfStockPair;

  private final EntityDeletionOrUpdateAdapter<StockPair> __deletionAdapterOfStockPair;

  private final EntityDeletionOrUpdateAdapter<StockPair> __updateAdapterOfStockPair;

  public StockPairDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfStockPair = new EntityInsertionAdapter<StockPair>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR ABORT INTO `stock_pairs` (`id`,`ticker1`,`ticker2`,`companyName1`,`companyName2`,`priceDifference`,`notifyWhenEqual`) VALUES (nullif(?, 0),?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final StockPair entity) {
        statement.bindLong(1, entity.getId());
        if (entity.getTicker1() == null) {
          statement.bindNull(2);
        } else {
          statement.bindString(2, entity.getTicker1());
        }
        if (entity.getTicker2() == null) {
          statement.bindNull(3);
        } else {
          statement.bindString(3, entity.getTicker2());
        }
        if (entity.getCompanyName1() == null) {
          statement.bindNull(4);
        } else {
          statement.bindString(4, entity.getCompanyName1());
        }
        if (entity.getCompanyName2() == null) {
          statement.bindNull(5);
        } else {
          statement.bindString(5, entity.getCompanyName2());
        }
        statement.bindDouble(6, entity.getPriceDifference());
        final int _tmp = entity.getNotifyWhenEqual() ? 1 : 0;
        statement.bindLong(7, _tmp);
      }
    };
    this.__deletionAdapterOfStockPair = new EntityDeletionOrUpdateAdapter<StockPair>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "DELETE FROM `stock_pairs` WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final StockPair entity) {
        statement.bindLong(1, entity.getId());
      }
    };
    this.__updateAdapterOfStockPair = new EntityDeletionOrUpdateAdapter<StockPair>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE OR ABORT `stock_pairs` SET `id` = ?,`ticker1` = ?,`ticker2` = ?,`companyName1` = ?,`companyName2` = ?,`priceDifference` = ?,`notifyWhenEqual` = ? WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final StockPair entity) {
        statement.bindLong(1, entity.getId());
        if (entity.getTicker1() == null) {
          statement.bindNull(2);
        } else {
          statement.bindString(2, entity.getTicker1());
        }
        if (entity.getTicker2() == null) {
          statement.bindNull(3);
        } else {
          statement.bindString(3, entity.getTicker2());
        }
        if (entity.getCompanyName1() == null) {
          statement.bindNull(4);
        } else {
          statement.bindString(4, entity.getCompanyName1());
        }
        if (entity.getCompanyName2() == null) {
          statement.bindNull(5);
        } else {
          statement.bindString(5, entity.getCompanyName2());
        }
        statement.bindDouble(6, entity.getPriceDifference());
        final int _tmp = entity.getNotifyWhenEqual() ? 1 : 0;
        statement.bindLong(7, _tmp);
        statement.bindLong(8, entity.getId());
      }
    };
  }

  @Override
  public Object insertStockPair(final StockPair stockPair,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfStockPair.insert(stockPair);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteStockPair(final StockPair stockPair,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __deletionAdapterOfStockPair.handle(stockPair);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object updateStockPair(final StockPair stockPair,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __updateAdapterOfStockPair.handle(stockPair);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object getAllStockPairs(final Continuation<? super List<StockPair>> $completion) {
    final String _sql = "SELECT `stock_pairs`.`id` AS `id`, `stock_pairs`.`ticker1` AS `ticker1`, `stock_pairs`.`ticker2` AS `ticker2`, `stock_pairs`.`companyName1` AS `companyName1`, `stock_pairs`.`companyName2` AS `companyName2`, `stock_pairs`.`priceDifference` AS `priceDifference`, `stock_pairs`.`notifyWhenEqual` AS `notifyWhenEqual` FROM stock_pairs";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<StockPair>>() {
      @Override
      @NonNull
      public List<StockPair> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = 0;
          final int _cursorIndexOfTicker1 = 1;
          final int _cursorIndexOfTicker2 = 2;
          final int _cursorIndexOfCompanyName1 = 3;
          final int _cursorIndexOfCompanyName2 = 4;
          final int _cursorIndexOfPriceDifference = 5;
          final int _cursorIndexOfNotifyWhenEqual = 6;
          final List<StockPair> _result = new ArrayList<StockPair>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final StockPair _item;
            final int _tmpId;
            _tmpId = _cursor.getInt(_cursorIndexOfId);
            final String _tmpTicker1;
            if (_cursor.isNull(_cursorIndexOfTicker1)) {
              _tmpTicker1 = null;
            } else {
              _tmpTicker1 = _cursor.getString(_cursorIndexOfTicker1);
            }
            final String _tmpTicker2;
            if (_cursor.isNull(_cursorIndexOfTicker2)) {
              _tmpTicker2 = null;
            } else {
              _tmpTicker2 = _cursor.getString(_cursorIndexOfTicker2);
            }
            final String _tmpCompanyName1;
            if (_cursor.isNull(_cursorIndexOfCompanyName1)) {
              _tmpCompanyName1 = null;
            } else {
              _tmpCompanyName1 = _cursor.getString(_cursorIndexOfCompanyName1);
            }
            final String _tmpCompanyName2;
            if (_cursor.isNull(_cursorIndexOfCompanyName2)) {
              _tmpCompanyName2 = null;
            } else {
              _tmpCompanyName2 = _cursor.getString(_cursorIndexOfCompanyName2);
            }
            final double _tmpPriceDifference;
            _tmpPriceDifference = _cursor.getDouble(_cursorIndexOfPriceDifference);
            final boolean _tmpNotifyWhenEqual;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfNotifyWhenEqual);
            _tmpNotifyWhenEqual = _tmp != 0;
            _item = new StockPair(_tmpId,_tmpTicker1,_tmpTicker2,_tmpCompanyName1,_tmpCompanyName2,_tmpPriceDifference,_tmpNotifyWhenEqual);
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

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
