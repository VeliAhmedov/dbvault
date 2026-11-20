package io.github.veliahmedov.dbvault.cli;

import io.github.veliahmedov.dbvault.exception.DbVaultException;
import io.github.veliahmedov.dbvault.notification.Notifier;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

// to test Operation cli, Mockito is used here as it depends on Notifier which sends webhook notifications
//as it is Operation does 2 things exe ops and send notification so test are build on these
public class OperationTest {
    // notifier is mock fake that record its calls, no network here
    private final Notifier notifier = mock(Notifier.class);

    // successful operation with details
    @Test
    void successReturnsZeroAndNotifiesWithDetail() throws Exception {
        // execute backup operation and send notification, backup -> ops name, testdb -> subject, notifier -> mock, lambda -> details
        int code = Operation.run("backup", "testdb", notifier, () -> "file.dump.gz | 10 bytes");
        // is ops success
        assertEquals(0, code);
        // mockito method, send has success ops and expected notification response from notifier (verify used here)
        verify(notifier).send(true, "[OK] dbvault backup testdb | file.dump.gz | 10 bytes");
        // check for unexpected, additional calls to mock
        verifyNoMoreInteractions(notifier);
    }

    // successful ops without details, some ops doesn't return details nor notification (ListCommand)
    @Test
    void successWithoutDetailHasNoSeparator() throws IOException {
        // list doesn't send notification so run it, if detail empty, suffix should also be empty
        Operation.run("list", "s3", notifier, () -> "");
        // ArgumentCaptor create method that capture argument passed to mock method
        // it lets to capture message and inspect it separately like rethrowing
        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        // send is called with true and capture second argument
        verify(notifier).send(eq(true), message.capture());
        // end result should be given value
        assertEquals("[OK] dbvault list s3", message.getValue());
    }

    // in case of failure on notification part and rethrowing exception
    @Test
    void failureNotifiesAndRethrowsTheSameException() {
        // exception we will throw
        DbVaultException problem = new DbVaultException("problem");
        // instead of success, lambda throws exception
        DbVaultException thrown = assertThrows(DbVaultException.class,
                () -> Operation.run("backup", "testdb", notifier, () -> { throw problem; }));
        // see if thrown exception and excepted are same (same not equal because object identity should be same)
        assertSame(problem, thrown);
        verify(notifier).send(false, "[FAILED] dbvault backup testdb: problem");
        verifyNoMoreInteractions(notifier);
    }

    // Operation catches 2 types of failures, RuntimeException and IOException, this checks latter
    @Test
    void ioFailureIsAlsoReportedAndRethrown() {
        assertThrows(IOException.class,
                () -> Operation.run("verify", "x.dump.gz", notifier, () -> { throw new IOException("disk"); }));

        verify(notifier).send(false, "[FAILED] dbvault verify x.dump.gz: disk");
    }

    //success shouldn't report failure
    @Test
    void successDoesNotReportFailure() throws IOException {
        Operation.run("backup", "testdb", notifier, () -> "ok");
        // never() -> method call shouldn't happen 0 times, any arg is acceptable
        // in short, verify that send() was never called with false and any string message
        verify(notifier, never()).send(eq(false), anyString());
    }
}
