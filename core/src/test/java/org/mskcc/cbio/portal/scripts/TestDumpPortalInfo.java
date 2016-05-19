/*
 * Copyright (c) 2016 The Hyve B.V.
 *
 * This code is licensed under the GNU Affero General Public License (AGPL),
 * version 3, or (at your option) any later version.
 *
 * This file is part of cBioPortal.
 *
 * cBioPortal is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.mskcc.cbio.portal.scripts;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.security.Permission;

import org.junit.Before;
import org.junit.After;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mskcc.cbio.portal.util.SpringUtil;

import static org.junit.Assert.*;
import static org.junit.Assume.*;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.transaction.TransactionConfiguration;

/**
 * Tests for the script that exports a directory of JSON validator input files.
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"classpath:/applicationContext-dao.xml"})
@TransactionConfiguration(transactionManager = "transactionManager", defaultRollback = true)
@Transactional
public class TestDumpPortalInfo {

    // exit status codes for the script
    private static final int EX_USAGE = 64;
    private static final int EX_IOERR = 74;

    @Rule
    public final TemporaryFolder tempFolder = new TemporaryFolder();

    // TODO: use ExpectedSystemExit and SystemErrRule from the package
    // system-rules (org.junit.contrib.java.lang.system) if cBioPortal
    // updates JUnit to 4.9+

    private PrintStream origSystemErr;
    protected ByteArrayOutputStream systemErrStream;

    private SecurityManager origSecurityManager;

    /**
     * Security manager to throw an exception when System.exit() is called.
     */
    private static class NoExitSecurityManager extends SecurityManager {
        @Override
        public void checkExit(int status) {
            throw new ExitException(status);
        }
        // allow everything else
        @Override
        public void checkPermission(Permission perm) {};
        @Override
        public void checkPermission(Permission perm, Object context) {};
    }

    /**
     * Exception to be thrown instead of exiting when System.exit() is called
     */
    protected static class ExitException extends SecurityException {

        private final int status;

        /**
         * Instantiate an ExitException for a call with the given exit status
         *
         * @param status  the exit status code
         */
        public ExitException(int status) 
        {
            super("System.exit() was called with status " + status);
            this.status = status;
        }

        /**
         * Return the exit status with which System.exit() was called
         *
         * @return the exit status
         */
        public int getStatus() {
            return status;
        }
    }

    /**
     * Adjust System to throw on exit and keep track of standard error output
     * 
     * Calls to System.exit() will result in an ExitException (set with the
     * exit status) and anything written to System.err will instead be stored
     * in systemErrStream for use in tests, until tearDown is called.
     */
    @Before
    public void setUp() {
        // set the security manager to raise an exception on System.exit()
        origSecurityManager = System.getSecurityManager();
        System.setSecurityManager(new NoExitSecurityManager());
        // replace
        systemErrStream = new ByteArrayOutputStream();
        origSystemErr = System.err;
        System.setErr(new PrintStream(systemErrStream));
    }

    /**
     * Restore the environment to before setUp() was called
     */
    @After
    public void tearDown() {
        System.setErr(origSystemErr);
        systemErrStream = null;
        System.setSecurityManager(origSecurityManager);
    }

    /**
     * Test if the output is properly written on basic usage
     */
    @Test
    public void testBasicUsage() throws Exception {
        // create an empty folder
        File folder = tempFolder.newFolder("folder");
        // define a non-existing filename in the new folder
        File outDir = new File(folder, "outDir");

        // Run the script
        String[] args = {outDir.getPath()};
        DumpPortalInfo.main(args);

        // check if the output dir was created in the folder
        assertEquals(folder.list().length, 1);
        assertTrue(
                "Script did not create directory " + outDir.getPath(),
                outDir.isDirectory());
        // check if the expected files were created in the output dir
        assertEquals(outDir.list().length, 5);
        // TODO: read reference files as generated from the unit test db
        File [] referenceFiles = {};
        for (File expectedFile : referenceFiles) {
            assertTrue(
                    "Script did not create file " + expectedFile.getName(),
                    new File(outDir, expectedFile.getName()).isFile());
            // TODO: assert that the contents of the file match
        }
    }

    /**
     * Test whether a usage message is printed if no output dirname is supplied.
     */
    @Test
    public void testNoArgs() throws Exception {
        boolean exitWasCalled = false;
        String[] args = {};
        // catch exception if System.exit was called
        try {
            DumpPortalInfo.main(args);
        } catch (ExitException e) {
            exitWasCalled = true;
            assertEquals(e.getStatus(), EX_USAGE);
        }
        assertTrue("Script did not call System.exit()", exitWasCalled);
        assertTrue(
                String.format(
                        "No usage message was printed to standard error: %s",
                        systemErrStream.toString()),
                systemErrStream.toString().toLowerCase().contains( "usage:"));
    }

}
