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

import java.io.File;

import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;

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

    @Rule
    public ExpectedException exception = ExpectedException.none();
    @Rule
    public final TemporaryFolder tempFolder = new TemporaryFolder();

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
        Runnable runner = new DumpPortalInfo(args);
        runner.run();

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
        exception.expect(UsageException.class);
        exception.expectMessage("dumpPortalInfo");
        String[] args = {};
        Runnable runner = new DumpPortalInfo(args);
        runner.run();
    }
}
