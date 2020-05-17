package com.justin.distribute.election.zab.data;

import com.justin.distribute.election.zab.common.PropertiesUtil;
import com.justin.net.remoting.common.Pair;
import com.justin.net.remoting.protocol.JSONSerializable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;

import java.io.File;
import java.nio.charset.Charset;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * WX: coding到灯火阑珊
 * @author Justin
 */
public class DataManager {
    private static final Logger logger = LogManager.getLogger(DataManager.class.getSimpleName());

    private final static byte[] LAST_INDEX_KEY = "last_index_key".getBytes(Charset.forName("UTF-8"));
    private final ReadWriteLock committedLock = new ReentrantReadWriteLock();
    private final ReadWriteLock logLock = new ReentrantReadWriteLock();

    private static RocksDB logDb;
    private static RocksDB committedDb;

    static {
        RocksDB.loadLibrary();
    }

    private DataManager() {
        try {
            File committedFile = new File(PropertiesUtil.getCommittedDir());
            if (!committedFile.exists()) {
                committedFile.mkdirs();
            }
            File logFile = new File(PropertiesUtil.getLogDir());
            if (!logFile.exists()) {
                logFile.mkdirs();
            }

            Options options = new Options();
            options.setCreateIfMissing(true);
            committedDb = RocksDB.open(options, PropertiesUtil.getCommittedDir());
            logDb = RocksDB.open(options, PropertiesUtil.getLogDir());
        }catch (Exception e) {
            logger.error(e);
        }
    }

    private static class DataMgrSingle {
        private final static DataManager INSTANCE = new DataManager();
    }

    public static DataManager getInstance() {
        return DataMgrSingle.INSTANCE;
    }

    public boolean put(final String key, final String value) {
        try {
            if (logLock.writeLock().tryLock(3000, TimeUnit.MILLISECONDS)) {
                logDb.put(key.getBytes(Charset.forName("UTF-8")), JSONSerializable.encode(value));
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            logLock.writeLock().unlock();
        }
        return false;
    }

    public String get(final String key) {
        try {
            if (logLock.readLock().tryLock(3000, TimeUnit.MILLISECONDS)) {
                byte[] value = logDb.get(key.getBytes(Charset.forName("UTF-8")));
                if (value == null) {
                    return null;
                }
                return JSONSerializable.decode(value, String.class);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            logLock.readLock().unlock();
        }
        return null;
    }

    public boolean write(final Data data) {
        try {
            if (committedLock.writeLock().tryLock(3000, TimeUnit.MILLISECONDS)) {
                Long nextIndex = getLastIndex() + 1;
                data.getZxId().setCounter(nextIndex);
                committedDb.put(nextIndex.toString().getBytes("UTF-8"), JSONSerializable.encode(data));
                updateLastIndex(nextIndex);
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            committedLock.writeLock().unlock();
        }
        return false;
    }

    public Data read(Long index) {
        try {
            if (committedLock.readLock().tryLock(3000, TimeUnit.MILLISECONDS)) {
                byte[] value = committedDb.get(index.toString().getBytes("UTF-8"));
                if (value != null) {
                    return JSONSerializable.decode(value, Data.class);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            committedLock.readLock().unlock();
        }
        return new Data(new ZxId(0, index), new Pair<>("", ""));
    }

    public Data readLastData() {
        return read(getLastIndex());
    }

    public void removeFromIndex(Long index) {
        try {
            if (committedLock.writeLock().tryLock(3000, TimeUnit.MILLISECONDS)) {
                byte[] startIndex = index.toString().getBytes(Charset.forName("UTF-8"));
                byte[] lastIndex = getLastIndex().toString().getBytes(Charset.forName("UTF-8"));
                committedDb.deleteRange(startIndex, lastIndex);
                updateLastIndex(index - 1);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            committedLock.writeLock().unlock();
        }
    }

    public Long getLastIndex() {
        try {
            if (committedLock.readLock().tryLock(3000, TimeUnit.MILLISECONDS)) {
                byte[] lastIndex = committedDb.get(LAST_INDEX_KEY);
                if (lastIndex != null) {
                    return Long.valueOf(new String(lastIndex, Charset.forName("UTF-8")));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }finally {
            committedLock.readLock().unlock();
        }
        return -1l;
    }

    private void updateLastIndex(final Long index) {
        try {
            committedDb.put(LAST_INDEX_KEY, index.toString().getBytes(Charset.forName("UTF-8")));
        } catch (RocksDBException e) {
            e.printStackTrace();
        }
    }

    public void close() {
        if (committedDb != null) {
            committedDb.close();
        }
        if (logDb != null) {
            logDb.close();
        }
    }
}
