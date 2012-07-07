/*
 * jGnash, a personal finance application
 * Copyright (C) 2001-2012 Craig Cavanaugh
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package jgnash.engine.xstream;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.reflection.PureJavaReflectionProvider;
import com.thoughtworks.xstream.io.binary.BinaryStreamDriver;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.nio.channels.FileLock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import jgnash.engine.CommodityNode;
import jgnash.engine.Config;
import jgnash.engine.ExchangeRate;
import jgnash.engine.RootAccount;
import jgnash.engine.StoredObject;
import jgnash.engine.StoredObjectComparator;
import jgnash.engine.budget.Budget;
import jgnash.engine.recurring.Reminder;
import jgnash.util.FileUtils;

/**
 * Simple object container for StoredObjects that reads and writes a xml file
 *
 * @author Craig Cavanaugh
 */
public class BinaryContainer extends AbstractXStreamContainer {

    protected BinaryContainer(final File file) {
        super(file);
    }

    @Override
    void commit() {
        writeBinary();
    }

    private synchronized void writeBinary() {
        readWriteLock.readLock().lock();

        try {
            releaseFileLock();
            writeBinary(objects, file);
        } finally {
            acquireFileLock();
            readWriteLock.readLock().unlock();
        }
    }

    /**
     * Writes an XML file given a collection of StoredObjects. TrashObjects and objects marked for removal are not
     * written. If the file already exists, it will be overwritten.
     *
     * @param objects Collection of StoredObjects to write
     * @param file    file to write
     */
    public static synchronized void writeBinary(final Collection<StoredObject> objects, final File file) {
        Logger logger = Logger.getLogger(BinaryContainer.class.getName());

        if (file.exists()) {
            File backup = new File(file.getAbsolutePath() + ".backup");
            if (backup.exists()) {
                if (!backup.delete()) {
                    logger.log(Level.WARNING, "Was not able to delete the old backup file: {0}", backup.getAbsolutePath());
                }
            }

            FileUtils.copyFile(file, backup);
        }

        List<StoredObject> list = new ArrayList<>();

        list.addAll(query(objects, Budget.class));
        list.addAll(query(objects, Config.class));
        list.addAll(query(objects, CommodityNode.class));
        list.addAll(query(objects, ExchangeRate.class));
        list.addAll(query(objects, RootAccount.class));
        list.addAll(query(objects, Reminder.class));

        // remove any objects marked for removal
        Iterator<StoredObject> i = list.iterator();
        while (i.hasNext()) {
            StoredObject o = i.next();
            if (o.isMarkedForRemoval()) {
                i.remove();
            }
        }

        // sort the list
        Collections.sort(list, new StoredObjectComparator());

        logger.info("Writing Binary file");

        try (OutputStream os = new BufferedOutputStream(new FileOutputStream(file))) {

            XStream xstream = configureXStream(new XStream(new PureJavaReflectionProvider(), new BinaryStreamDriver()));

            try (ObjectOutputStream out = xstream.createObjectOutputStream(os)) {
                out.writeObject(list);
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, e.getLocalizedMessage(), e);
        }

        logger.info("Writing Binary file complete");
    }

    void readBinary() {

        ObjectInputStream in = null;
        FileLock readLock = null; // obtain a shared lock for reading
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(file);
        } catch (FileNotFoundException e) {
            Logger.getLogger(BinaryContainer.class.getName()).log(Level.SEVERE, null, e);
        }

        InputStream inputStream = new BufferedInputStream(fis);

        readWriteLock.writeLock().lock();

        try {
            XStream xstream = configureXStream(new XStream(new StoredObjectReflectionProvider(objects), new BinaryStreamDriver()));

            readLock = fis.getChannel().tryLock(0, Long.MAX_VALUE, true);

            if (readLock != null) {
                in = xstream.createObjectInputStream(inputStream);
                in.readObject();
            }
        } catch (ClassNotFoundException | IOException ex) {
            Logger.getLogger(BinaryContainer.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            if (in != null) {
                try {
                    if (readLock != null) {
                        readLock.release();
                    }
                    in.close();
                } catch (IOException e) {
                    Logger.getLogger(BinaryContainer.class.getName()).log(Level.SEVERE, null, e);
                }
            }

            try {
                inputStream.close();
            } catch (IOException e) {
                Logger.getLogger(BinaryContainer.class.getName()).log(Level.SEVERE, null, e);
            }

            try {
                fis.close();
            } catch (IOException e) {
                Logger.getLogger(BinaryContainer.class.getName()).log(Level.SEVERE, null, e);
            }

            acquireFileLock(); // lock the file on open

            readWriteLock.writeLock().unlock();
        }
    }
}