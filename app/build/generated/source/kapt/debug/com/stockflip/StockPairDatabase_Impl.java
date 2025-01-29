package com.stockflip;

import androidx.annotation.NonNull;
import androidx.room.DatabaseConfiguration;
import androidx.room.InvalidationTracker;
import androidx.room.RoomDatabase;
import androidx.room.RoomOpenHelper;
import androidx.room.migration.AutoMigrationSpec;
import androidx.room.migration.Migration;
import androidx.room.util.DBUtil;
import androidx.room.util.TableInfo;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import java.lang.Class;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@SuppressWarnings({"unchecked", "deprecation"})
public final class StockPairDatabase_Impl extends StockPairDatabase {
  private volatile StockPairDao _stockPairDao;

  @Override
  @NonNull
  protected SupportSQLiteOpenHelper createOpenHelper(@NonNull final DatabaseConfiguration config) {
    final SupportSQLiteOpenHelper.Callback _openCallback = new RoomOpenHelper(config, new RoomOpenHelper.Delegate(5) {
      @Override
      public void createAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `stock_pairs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `ticker1` TEXT NOT NULL, `ticker2` TEXT NOT NULL, `companyName1` TEXT NOT NULL, `companyName2` TEXT NOT NULL, `priceDifference` REAL NOT NULL, `notifyWhenEqual` INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)");
        db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '27fc73864a4e2353f9d10002d92c02cf')");
      }

      @Override
      public void dropAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS `stock_pairs`");
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onDestructiveMigration(db);
          }
        }
      }

      @Override
      public void onCreate(@NonNull final SupportSQLiteDatabase db) {
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onCreate(db);
          }
        }
      }

      @Override
      public void onOpen(@NonNull final SupportSQLiteDatabase db) {
        mDatabase = db;
        internalInitInvalidationTracker(db);
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onOpen(db);
          }
        }
      }

      @Override
      public void onPreMigrate(@NonNull final SupportSQLiteDatabase db) {
        DBUtil.dropFtsSyncTriggers(db);
      }

      @Override
      public void onPostMigrate(@NonNull final SupportSQLiteDatabase db) {
      }

      @Override
      @NonNull
      public RoomOpenHelper.ValidationResult onValidateSchema(
          @NonNull final SupportSQLiteDatabase db) {
        final HashMap<String, TableInfo.Column> _columnsStockPairs = new HashMap<String, TableInfo.Column>(7);
        _columnsStockPairs.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsStockPairs.put("ticker1", new TableInfo.Column("ticker1", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsStockPairs.put("ticker2", new TableInfo.Column("ticker2", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsStockPairs.put("companyName1", new TableInfo.Column("companyName1", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsStockPairs.put("companyName2", new TableInfo.Column("companyName2", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsStockPairs.put("priceDifference", new TableInfo.Column("priceDifference", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsStockPairs.put("notifyWhenEqual", new TableInfo.Column("notifyWhenEqual", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysStockPairs = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesStockPairs = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoStockPairs = new TableInfo("stock_pairs", _columnsStockPairs, _foreignKeysStockPairs, _indicesStockPairs);
        final TableInfo _existingStockPairs = TableInfo.read(db, "stock_pairs");
        if (!_infoStockPairs.equals(_existingStockPairs)) {
          return new RoomOpenHelper.ValidationResult(false, "stock_pairs(com.stockflip.StockPair).\n"
                  + " Expected:\n" + _infoStockPairs + "\n"
                  + " Found:\n" + _existingStockPairs);
        }
        return new RoomOpenHelper.ValidationResult(true, null);
      }
    }, "27fc73864a4e2353f9d10002d92c02cf", "d5bb4b5308c55be6a10e26311fee5a27");
    final SupportSQLiteOpenHelper.Configuration _sqliteConfig = SupportSQLiteOpenHelper.Configuration.builder(config.context).name(config.name).callback(_openCallback).build();
    final SupportSQLiteOpenHelper _helper = config.sqliteOpenHelperFactory.create(_sqliteConfig);
    return _helper;
  }

  @Override
  @NonNull
  protected InvalidationTracker createInvalidationTracker() {
    final HashMap<String, String> _shadowTablesMap = new HashMap<String, String>(0);
    final HashMap<String, Set<String>> _viewTables = new HashMap<String, Set<String>>(0);
    return new InvalidationTracker(this, _shadowTablesMap, _viewTables, "stock_pairs");
  }

  @Override
  public void clearAllTables() {
    super.assertNotMainThread();
    final SupportSQLiteDatabase _db = super.getOpenHelper().getWritableDatabase();
    try {
      super.beginTransaction();
      _db.execSQL("DELETE FROM `stock_pairs`");
      super.setTransactionSuccessful();
    } finally {
      super.endTransaction();
      _db.query("PRAGMA wal_checkpoint(FULL)").close();
      if (!_db.inTransaction()) {
        _db.execSQL("VACUUM");
      }
    }
  }

  @Override
  @NonNull
  protected Map<Class<?>, List<Class<?>>> getRequiredTypeConverters() {
    final HashMap<Class<?>, List<Class<?>>> _typeConvertersMap = new HashMap<Class<?>, List<Class<?>>>();
    _typeConvertersMap.put(StockPairDao.class, StockPairDao_Impl.getRequiredConverters());
    return _typeConvertersMap;
  }

  @Override
  @NonNull
  public Set<Class<? extends AutoMigrationSpec>> getRequiredAutoMigrationSpecs() {
    final HashSet<Class<? extends AutoMigrationSpec>> _autoMigrationSpecsSet = new HashSet<Class<? extends AutoMigrationSpec>>();
    return _autoMigrationSpecsSet;
  }

  @Override
  @NonNull
  public List<Migration> getAutoMigrations(
      @NonNull final Map<Class<? extends AutoMigrationSpec>, AutoMigrationSpec> autoMigrationSpecs) {
    final List<Migration> _autoMigrations = new ArrayList<Migration>();
    return _autoMigrations;
  }

  @Override
  public StockPairDao stockPairDao() {
    if (_stockPairDao != null) {
      return _stockPairDao;
    } else {
      synchronized(this) {
        if(_stockPairDao == null) {
          _stockPairDao = new StockPairDao_Impl(this);
        }
        return _stockPairDao;
      }
    }
  }
}
