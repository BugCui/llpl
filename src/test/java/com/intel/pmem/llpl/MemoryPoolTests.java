/*
 * Copyright (C) 2020 Intel Corporation
 *
 * SPDX-License-Identifier: BSD-3-Clause
 *
 */

package com.intel.pmem.llpl;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.testng.Assert;
import org.testng.SkipException;
import java.util.Arrays;

@Test(singleThreaded = true)
public class MemoryPoolTests {
	MemoryPool pool = null;

	@BeforeMethod
	public void initialize() {
		pool = null;
        if (TestVars.ISDAX) pool = MemoryPool.createPool(TestVars.HEAP_USER_PATH + TestVars.HEAP_NAME, 0L);
        else pool = MemoryPool.createPool(TestVars.HEAP_USER_PATH + TestVars.HEAP_NAME, TestVars.HEAP_SIZE);
        Assert.assertTrue(pool != null);
        if (TestVars.ISDAX) Assert.assertTrue(pool.size() > 0);
        else Assert.assertEquals(TestVars.HEAP_SIZE, pool.size());
	}

	@SuppressWarnings("deprecation")
	@AfterMethod
	public void testCleanup() {
		if (TestVars.ISDAX) {
			TestVars.daxCleanUp();
		}
		else TestVars.cleanUp(TestVars.HEAP_USER_PATH + TestVars.HEAP_NAME);
	}

    @Test
    public void testCloseAndReopenPool(){
        long offset = 0;
        long value = 42L;
        pool.setLong(offset, value);
        pool.flush(offset, Long.BYTES);
        ((MemoryPoolImpl)pool).close();
        pool = null;
        // recreate pool in same location, should return existing pool
        pool = MemoryPool.openPool(TestVars.HEAP_USER_PATH + TestVars.HEAP_NAME);
        Assert.assertTrue(pool != null);
        if (TestVars.ISDAX) Assert.assertTrue(pool.size() > 0);
        else Assert.assertEquals(TestVars.HEAP_SIZE, pool.size());
        Assert.assertEquals(value, pool.getLong(offset));
    }

    @Test
    public void testOpenPoolDirectoryPath(){
        // create pool with a path only
        try {
            pool = MemoryPool.openPool("/root/");
            Assert.fail("IllegalArgumentException was not thrown");
        } catch(IllegalArgumentException e) {
            Assert.assertTrue(true);
        }
    }

    @Test
    public void testOpenPoolNonExistentPath(){
        // create pool with a non-existent path
        try {
            pool = MemoryPool.openPool("/path/to/nonexistent/directory/");
            Assert.fail("IllegalArgumentException was not thrown");
        } catch(IllegalArgumentException e) {
            Assert.assertTrue(true);
        }
    }

    @Test
    public void testMemoryPoolByteAccessMethods(){
        byte value = (byte)255;
        long offset = 0;
        pool.setByte(offset, value);
        Assert.assertEquals(value, pool.getByte(offset));
    }

    @Test
    public void testMemoryPoolShortAccessMethods(){
        short value = (short)65432;
        long offset = 0;
        pool.setShort(offset, value);
        Assert.assertEquals(value, pool.getShort(offset));
    }

    @Test
    public void testMemoryPoolIntAccessMethods(){
        int value = 987654321;
        long offset = 0;
        pool.setInt(offset, value);
        Assert.assertEquals(value, pool.getInt(offset));
    }

    @Test
    public void testMemoryPoolLongAccessMethods(){
        long value = 1413121110987654321L;
        long offset = 0;
        pool.setLong(offset,value);
        Assert.assertEquals(value, pool.getLong(offset));
    }

    @Test
    public void testMemoryPoolCopyMemory(){
        String string1 = "Pluto and Saturn"; // len = 16
        int byteCount = string1.length();
        byte[] bytes1 = string1.getBytes();
        byte[] bytes2 = new byte[bytes1.length];
        long offset = 16;
        long offset2 = 32;
        int index = 0;
        pool.copyFromByteArray(bytes1, index, offset, byteCount);
        pool.flush(offset, byteCount);
        pool.copyFromPool(offset, offset2, byteCount);
        pool.flush(offset2, byteCount);
        pool.copyToByteArray(offset2, bytes2, index, byteCount);
        Assert.assertEquals((new String(bytes2)), string1);
    }

    @Test
    public void testMemoryPoolCopyMemoryNT(){
        String string1 = "Pluto and Saturn"; // len = 16
        int byteCount = string1.length();
        byte[] bytes1 = string1.getBytes();
        byte[] bytes2 = new byte[bytes1.length];
        long offset = 16;
        long offset2 = 32;
        int index = 0;
        pool.copyFromByteArrayNT(bytes1, index, offset, byteCount);
        pool.copyFromPoolNT(offset, offset2, byteCount);
        pool.copyToByteArray(offset2, bytes2, index, byteCount);
        Assert.assertEquals((new String(bytes2)), string1);
    }

    @Test
    public void testMemoryPoolSetMemory(){
        long numBytes = 1024 * 1024;
        byte value = 127;
        long offset = 0;
        pool.setMemory(value, offset, numBytes);
        pool.flush(offset, numBytes);
        for (long i = 0; i < numBytes; i++){
            Assert.assertEquals(pool.getByte(i), value);
        }
    }

    @Test
    public void testMemoryPoolSetMemoryNT(){
        long numBytes = 1024 * 1024;
        byte value = 127;
        long offset = 0;
        pool.setMemoryNT(value, offset, numBytes);
        for (long i = 0; i < numBytes; i++){
            Assert.assertEquals(pool.getByte(i), value);
        }
    }

    @Test
    public void testMemoryPoolOpenClosePool(){
        byte value = (byte)85;
        long numBytes = 1024 * 1024;
        long offset = 0;
        pool.setMemoryNT(value, offset, numBytes);
        ((MemoryPoolImpl)pool).close();
        pool = MemoryPool.openPool(TestVars.HEAP_USER_PATH + TestVars.HEAP_NAME);
        for (long i = 0; i < numBytes; i++){
            Assert.assertEquals(pool.getByte(i), value);
        }
    }

    @Test
    public void testInterPoolCopy() {
        if (TestVars.ISDAX) throw new SkipException("Test not valid in DAX mode");
        String srcPoolName = TestVars.HEAP_USER_PATH + TestVars.HEAP_NAME + "2";
        MemoryPool srcPool = MemoryPool.createPool(srcPoolName, TestVars.HEAP_SIZE);
        Assert.assertEquals(TestVars.HEAP_SIZE, srcPool.size());
        // populate srcPool
        long numBytes = 128;
        byte value = 42;
        long offset = 0;
        srcPool.setMemory(value, offset, numBytes);
        pool.copyFromPool(srcPool, offset, offset, numBytes);
        pool.flush(offset, numBytes);
        for (int i = 0; i < numBytes; i++ ){
            Assert.assertEquals(value, pool.getByte(i));
        }
        Assert.assertTrue(TestVars.cleanUp(srcPoolName));
    }

    @Test
    public void testInterPoolCopyNT() {
        if (TestVars.ISDAX) throw new SkipException("Test not valid in DAX mode");
        String srcPoolName = TestVars.HEAP_USER_PATH + TestVars.HEAP_NAME + "2";
        MemoryPool srcPool = MemoryPool.createPool(srcPoolName, TestVars.HEAP_SIZE);
        Assert.assertEquals(TestVars.HEAP_SIZE, srcPool.size());
        // populate srcPool
        long numBytes = 128;
        byte value = 42;
        long offset = 0;
        // Non-temporal (non-cache-populating) stores
        srcPool.setMemoryNT(value, offset, numBytes);
        pool.copyFromPoolNT(srcPool, offset, offset, numBytes);
        for (int i = 0; i < numBytes; i++ ){
            Assert.assertEquals(value, pool.getByte(i));
        }
        Assert.assertTrue(TestVars.cleanUp(srcPoolName));
    }

    @Test
    public void testByteArrayCopyOffsetTooLarge(){
        byte arrayLen = 64;
        byte[] bArray = new byte[arrayLen];
        for (byte i = 0; i < arrayLen; i++){
            bArray[i] = i;
        }
        try {
            pool.copyFromByteArray(bArray, 0, pool.size() + 1024, arrayLen);
            Assert.fail("IndexOutOfBoundsException wasn't thrown");
        } catch(IndexOutOfBoundsException e) {
            Assert.assertTrue(true);
        }
    }

    @Test
    public void testByteArrayCopyExceptionHandlingNegativeOffset(){
        byte arrayLen = 64;
        byte[] bArray = new byte[arrayLen];
        for (byte i = 0; i < arrayLen; i++){
            bArray[i] = i;
        }
        // Negative offset
        try {
            pool.copyFromByteArray(bArray, 0, -1, arrayLen);
            Assert.fail("IndexOutOfBoundsException wasn't thrown");
        } catch(IndexOutOfBoundsException e) {
            Assert.assertTrue(true);
        }
    }

    @Test
    public void testByteArrayCopyExceptionHandlingNegativeIndex(){
        byte arrayLen = 64;
        byte[] bArray = new byte[arrayLen];
        for (byte i = 0; i < arrayLen; i++){
            bArray[i] = i;
        }
        // Negative Index
        try {
            pool.copyFromByteArray(bArray, -1, 0, arrayLen);
            Assert.fail("IndexOutOfBoundsException wasn't thrown");
        } catch(IndexOutOfBoundsException e) {
            Assert.assertTrue(true);
        }
    }

    @Test
    public void testByteArrayCopyExceptionHandlingIndexTooLarge(){
        byte arrayLen = 64;
        byte[] bArray = new byte[arrayLen];
        for (byte i = 0; i < arrayLen; i++){
            bArray[i] = i;
        }
        // Index too large
        try {
            pool.copyFromByteArray(bArray, 0, 0, arrayLen + 1024);
            Assert.fail("IndexOutOfBoundsException wasn't thrown");
        } catch(IndexOutOfBoundsException e) {
            Assert.assertTrue(true);
        }
    }

    @Test
    public void testByteArrayCopyNTExceptionHandlingNegativeOffset(){
        byte arrayLen = 64;
        byte[] bArray = new byte[arrayLen];
        for (byte i = 0; i < arrayLen; i++){
            bArray[i] = i;
        }
        // Negative offset, NT copy
        try {
            pool.copyFromByteArrayNT(bArray, 0, -1, arrayLen);
            Assert.fail("IndexOutOfBoundsException wasn't thrown");
        } catch(IndexOutOfBoundsException e) {
            Assert.assertTrue(true);
        }
    }

    @Test
    public void testByteArrayCopyNTExceptionHandlingNegativeIndex(){
        byte arrayLen = 64;
        byte[] bArray = new byte[arrayLen];
        for (byte i = 0; i < arrayLen; i++){
            bArray[i] = i;
        }
        // Negative Index, NT copy
        try {
            pool.copyFromByteArrayNT(bArray, -1, 0, arrayLen);
            Assert.fail("IndexOutOfBoundsException wasn't thrown");
        } catch(IndexOutOfBoundsException e) {
            Assert.assertTrue(true);
        }
    }

    @Test
    public void testByteArrayCopyNTExceptionHandlingIndexTooLarge(){
        byte arrayLen = 64;
        byte[] bArray = new byte[arrayLen];
        for (byte i = 0; i < arrayLen; i++){
            bArray[i] = i;
        }
        // Index too large, NT copy
        try {
            pool.copyFromByteArrayNT(bArray, 0, 0, arrayLen + 1024);
            Assert.fail("IndexOutOfBoundsException wasn't thrown");
        } catch(IndexOutOfBoundsException e) {
            Assert.assertTrue(true);
        }
    }

    @Test
    public void testArrayCopyLengthTooLarge() {
        byte arrayLen = 64; // 8 * Long.Bytes
        byte[] bArray = new byte[arrayLen];
        for (byte i = 0; i < arrayLen; i++){
            bArray[i] = i;
        }
        try {
            pool.copyFromByteArray(bArray, 0, 0, arrayLen + 1024);
            Assert.fail("IndexOutOfBoundsException wasn't thrown");
        } catch(IndexOutOfBoundsException e) {
            Assert.assertTrue(true);
        }
    }

    @Test
    public void testCopyToByteArrayExceptionsNegativeOffset(){
        int numLongs = 8;
        int numBytes = numLongs * Long.BYTES; // 64
        byte[] bArray = new byte[numBytes];
        for (long i = 0; i < numLongs; i++){
            pool.setLong(i, i);
            Assert.assertEquals(i, pool.getLong(i));
        }
        // Negative offset
        try {
            pool.copyToByteArray(-1, bArray, 0, numBytes);
            Assert.fail("IndexOutOfBoundsException wasn't thrown");
        } catch(IndexOutOfBoundsException e) {
            Assert.assertTrue(true);
        }
    }

    @Test
    public void testCopyToByteArrayExceptionsNegativeByteCount(){
        int numLongs = 8;
        int numBytes = numLongs * Long.BYTES; // 64
        byte[] bArray = new byte[numBytes];
        for (long i = 0; i < numLongs; i++){
            pool.setLong(i, i);
            Assert.assertEquals(i, pool.getLong(i));
        }
        // Negative byteCount
        try {
            pool.copyToByteArray(0, bArray, 0, -1);
            Assert.fail("IndexOutOfBoundsException wasn't thrown");
        } catch(IndexOutOfBoundsException e) {
            Assert.assertTrue(true);
        }
    }

    @Test
    public void testCopyToByteArrayExceptionsNegativeIndex(){
        int numLongs = 8;
        int numBytes = numLongs * Long.BYTES; // 64
        byte[] bArray = new byte[numBytes];
        for (long i = 0; i < numLongs; i++){
            pool.setLong(i, i);
            Assert.assertEquals(i, pool.getLong(i));
        }
        // Negative Index
        try {
            pool.copyToByteArray(0, bArray, -1, numBytes);
            Assert.fail("IndexOutOfBoundsException wasn't thrown");
        } catch(IndexOutOfBoundsException e) {
            Assert.assertTrue(true);
        }
    }

    @Test
    public void testCopyToByteArrayExceptionsIndexTooLarge(){
        int numLongs = 8;
        int numBytes = numLongs * Long.BYTES; // 64
        byte[] bArray = new byte[numBytes];
        for (long i = 0; i < numLongs; i++){
            pool.setLong(i, i);
            Assert.assertEquals(i, pool.getLong(i));
        }
        // Index too large
        try {
            pool.copyToByteArray(0, bArray, 0, numBytes + 1024);
            Assert.fail("IndexOutOfBoundsException wasn't thrown");
        } catch(IndexOutOfBoundsException e) {
            Assert.assertTrue(true);
        }
    }

    @Test
    public void testPoolCreationExceptionsNegativePoolSize() {
        // negative pool size
        try {
            pool = new MemoryPoolImpl(TestVars.HEAP_USER_PATH + TestVars.HEAP_NAME, -1);
            Assert.fail("IllegalArgumentException wasn't thrown");
        } catch(IllegalArgumentException e) {
            Assert.assertTrue(true);
        }
    }

    @Test
    public void testPoolCreationExceptionsDevDaxNonzeroSize() {
        // nonzero size with /dev/dax path
        if (!TestVars.ISDAX) throw new SkipException("Test not valid in FSDAX mode");
        try {
            pool = new MemoryPoolImpl("/dev/dax", 1024);
            Assert.fail("IllegalArgumentException wasn't thrown");
        } catch(IllegalArgumentException e) {
            Assert.assertTrue(true);
        }
    }

    @Test
    public void testPoolCreationExceptionsZeroPoolSize() {
        // zero pool size
        if (TestVars.ISDAX) throw new SkipException("Test not valid in DAX mode");
        try {
            pool = new MemoryPoolImpl(TestVars.HEAP_USER_PATH + TestVars.HEAP_NAME, 0);
            Assert.fail("IllegalArgumentException wasn't thrown");
        } catch(IllegalArgumentException e) {
            Assert.assertTrue(true);
        }
    }

    @Test
    public void testPoolCreationExceptionsBadPathName() {
        // bad path name
        try {
            pool = MemoryPool.openPool("/bad/path/name" + TestVars.HEAP_NAME);
            Assert.fail("IllegalArgumentException wasn't thrown");
        } catch(IllegalArgumentException e) {
            Assert.assertTrue(true);
        }
    }

    // Functional validation tests:
    @Test
    public void testCreateMinimalSizePool() {
        // functional validation of minimal pool size
        if (TestVars.ISDAX) throw new SkipException("Test not valid in DAX mode");
        long poolSize = 1;
        pool = new MemoryPoolImpl(TestVars.HEAP_USER_PATH + TestVars.HEAP_NAME, poolSize);
        Assert.assertEquals(poolSize, pool.size());
    }

    // TODO: compare to test with flush.  Does the file copying cause a flush?
    // TODO: what are the durability promises of file-level operations?
    @Test
    public void testFileDataPersistenceNoFlush() {
        if (TestVars.ISDAX) throw new SkipException("Test not valid in DAX mode");
        String poolName = TestVars.HEAP_USER_PATH + TestVars.HEAP_NAME;
        String newPoolName = TestVars.HEAP_USER_PATH + TestVars.HEAP_NAME + "new";
        long offset = 0;
        long value = 42L;
        pool.setLong(offset, value);
        //pool.flush(offset, Long.BYTES);
        ((MemoryPoolImpl)pool).close();
        Assert.assertTrue(TestVars.copyFile(poolName, newPoolName));
        pool = null;
        // create pool from new location, compare to original data
        MemoryPool newPool = MemoryPool.openPool(newPoolName);
        Assert.assertEquals(value, newPool.getLong(offset));
        Assert.assertTrue(TestVars.cleanUp(newPoolName));
    }

    // TODO: compare to test without flush.  Does the file copying cause a flush?
    // TODO: what are the durability promises of file-level operations?
    @Test
    public void testFileDataPersistence() {
        if (TestVars.ISDAX) throw new SkipException("Test not valid in DAX mode");
        String poolName = TestVars.HEAP_USER_PATH + TestVars.HEAP_NAME;
        String newPoolName = TestVars.HEAP_USER_PATH + TestVars.HEAP_NAME + "new";
        long offset = 0;
        long value = 42L;
        pool.setLong(offset, value);
        pool.flush(offset, Long.BYTES);
        ((MemoryPoolImpl)pool).close();
        Assert.assertTrue(TestVars.copyFile(poolName, newPoolName));
        pool = null;
        // create pool from new location, compare to original data
        MemoryPool newPool = MemoryPool.openPool(newPoolName);
        Assert.assertEquals(value, newPool.getLong(offset));
        Assert.assertTrue(TestVars.cleanUp(newPoolName));
    }

    // MemoryPool.exists() method
    @Test
    public void testExistsFsDaxAffirmativeCase(){
        if (TestVars.ISDAX) throw new SkipException("Test not valid in DAX mode");
        String path = TestVars.HEAP_USER_PATH + TestVars.HEAP_NAME;
        Assert.assertTrue(pool.size() > 0);
        Assert.assertTrue(MemoryPool.exists(path));
    }

    @Test
    public void testExistsFsDaxNegativeCaseDirectory(){
        if (TestVars.ISDAX) throw new SkipException("Test not valid in DAX mode");
        String path = "/root/";
        Assert.assertFalse(MemoryPool.exists(path));
    }

    @Test
    public void testExistsFsDaxNegativeCaseNonExistentDirectory(){
        if (TestVars.ISDAX) throw new SkipException("Test not valid in DAX mode");
        String path = "/path/to/nonexistent/directory/";
        Assert.assertFalse(MemoryPool.exists(path));
    }

    @Test
    public void testExistsFsDaxNegativeCaseNonExistentFile(){
        if (TestVars.ISDAX) throw new SkipException("Test not valid in DAX mode");
        String path = "/path/to/nonexistent/pool";
        Assert.assertFalse(MemoryPool.exists(path));
    }

    @Test
    public void testExistsNegativeNullPath() {
        String path = null;
        try {
            MemoryPool.exists(path);
            Assert.fail("NullPointerException was not thrown.");
        } catch(NullPointerException e) {
            Assert.assertTrue(true);
        }
    }

    @Test
    public void testExistsDevDaxNegative(){
        if (!TestVars.ISDAX) throw new SkipException("Test not valid in FSDAX mode");
        String path = "/dev/dax9.9";
        Assert.assertFalse(MemoryPool.exists(path));
    }
}
