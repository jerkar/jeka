package org.jerkar.tool;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

@Ignore
public class FailureTest {

    @Test
    public void doAssertFailure() {
	Assert.fail("it must fail");
    }

    @Test
    public void doExceptionraise() {
	throw new RuntimeException(new RuntimeException("exception2"));
    }

    @Test
    @Ignore
    public void doIgnore() {
	throw new RuntimeException();
    }

}
