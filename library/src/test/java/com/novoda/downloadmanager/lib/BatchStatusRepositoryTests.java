package com.novoda.downloadmanager.lib;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.net.Uri;
import android.support.annotation.NonNull;

import com.novoda.downloadmanager.lib.DownloadContract.Batches;
import com.novoda.downloadmanager.lib.DownloadContract.Downloads;
import com.novoda.notils.string.StringUtils;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.mockito.InOrder;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import static org.fest.assertions.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyCollection;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.*;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.whenNew;

@RunWith(Enclosed.class)
public class BatchStatusRepositoryTests {

    private static final long ANY_BATCH_ID = 1;
    private static final long ANY_DOWNLOAD_ID = 2l;
    private static final int ANY_BATCH_STATUS = DownloadStatus.RUNNING;

    private static final Uri PUBLICLY_ACCESSIBLE_DOWNLOADS_URI = mock(Uri.class);
    private static final Uri DOWNLOADS_BY_BATCH_URI = mock(Uri.class);
    private static final Uri ALL_DOWNLOADS_URI = mock(Uri.class);
    private static final Uri BATCHES_URI = mock(Uri.class);
    private static final Uri BATCH_BY_ID_URI = mock(Uri.class);
    private static final Uri CONTENT_URI = mock(Uri.class);
    private static final Uri DOWNLOADS_WITHOUT_PROGRESS_URI = mock(Uri.class);
    private static final Uri BATCHES_WITHOUT_PROGRESS_URI = mock(Uri.class);

    private static final long CURRENT_TIME_MILLIS = 1l;

    @RunWith(Parameterized.class)
    public static class GetBatchStatus {

        @Parameters
        public static Collection<Object[]> data() {
            return Arrays.asList(new Object[][]{
                    {DownloadStatus.PAUSING},
                    {DownloadStatus.QUEUED_DUE_CLIENT_RESTRICTIONS},
                    {DownloadStatus.DELETING},
                    {DownloadStatus.SUBMITTED},
                    {DownloadStatus.PENDING},
                    {DownloadStatus.RUNNING},
                    {DownloadStatus.PAUSED_BY_APP},
                    {DownloadStatus.WAITING_TO_RETRY},
                    {DownloadStatus.WAITING_FOR_NETWORK},
                    {DownloadStatus.QUEUED_FOR_WIFI},
                    {DownloadStatus.INSUFFICIENT_SPACE_ERROR},
                    {DownloadStatus.DEVICE_NOT_FOUND_ERROR},
                    {DownloadStatus.SUCCESS},
                    {DownloadStatus.BAD_REQUEST},
                    {DownloadStatus.NOT_ACCEPTABLE},
                    {DownloadStatus.LENGTH_REQUIRED},
                    {DownloadStatus.PRECONDITION_FAILED},
                    {DownloadStatus.MIN_ARTIFICIAL_ERROR_STATUS},
                    {DownloadStatus.FILE_ALREADY_EXISTS_ERROR},
                    {DownloadStatus.CANNOT_RESUME},
                    {DownloadStatus.CANCELED},
                    {DownloadStatus.UNKNOWN_ERROR},
                    {DownloadStatus.FILE_ERROR},
                    {DownloadStatus.UNHANDLED_REDIRECT},
                    {DownloadStatus.UNHANDLED_HTTP_CODE},
                    {DownloadStatus.HTTP_DATA_ERROR},
                    {DownloadStatus.HTTP_EXCEPTION},
                    {DownloadStatus.TOO_MANY_REDIRECTS},
                    {DownloadStatus.BATCH_FAILED},
            });
        }

        private int expectedStatus;

        public GetBatchStatus(int expectedStatus) {
            this.expectedStatus = expectedStatus;
        }

        @Test
        public void givenAStatusThenThatStatusIsRetrieved() throws Exception {
            BatchStatusRepository service = givenABatchWithStatus(ANY_BATCH_ID, expectedStatus);

            int batchStatus = service.getBatchStatus(ANY_BATCH_ID);

            assertThat(batchStatus).isEqualTo(expectedStatus);
        }

        private BatchStatusRepository givenABatchWithStatus(long batchId, int status) {
            SystemFacade mockSystemFacade = mock(SystemFacade.class);
            DownloadsUriProvider mockDownloadsUriProvider = mock(DownloadsUriProvider.class);
            when(mockDownloadsUriProvider.getSingleBatchUri(batchId)).thenReturn(BATCH_BY_ID_URI);

            MockCursorWithStatuses mockCursorWithStatuses = new MockCursorWithStatuses(status);
            ContentResolver mockResolver = mock(ContentResolver.class);

            String[] projection = {Batches.COLUMN_STATUS};

            when(mockResolver.query(BATCH_BY_ID_URI, projection, null, null, null))
                    .thenReturn(mockCursorWithStatuses);
            return new BatchStatusRepository(mockResolver, mockDownloadsUriProvider, mockSystemFacade);
        }

    }

    @RunWith(Parameterized.class)
    public static class CalculateBatchStatus {

        @Parameters
        public static Collection<Object[]> data() {
            return Arrays.asList(new Object[][]{
                    {new Integer[]{DownloadStatus.SUCCESS, DownloadStatus.SUBMITTED}, DownloadStatus.RUNNING},
                    {new Integer[]{DownloadStatus.SUCCESS, DownloadStatus.BATCH_FAILED}, DownloadStatus.BATCH_FAILED},
                    {new Integer[]{DownloadStatus.SUBMITTED, DownloadStatus.SUBMITTED, DownloadStatus.SUBMITTED, DownloadStatus.SUCCESS}, DownloadStatus.RUNNING},
                    {new Integer[]{DownloadStatus.SUCCESS, DownloadStatus.SUCCESS}, DownloadStatus.SUCCESS},
                    {new Integer[]{DownloadStatus.PENDING, DownloadStatus.PENDING}, DownloadStatus.PENDING},
            });
        }

        private Integer[] statuses;

        private int expectedStatus;

        public CalculateBatchStatus(Integer[] statuses, int expectedStatus) {
            this.statuses = statuses;
            this.expectedStatus = expectedStatus;
        }

        @Test
        public void calculateCorrectStatusFromStatuses() throws Exception {
            BatchStatusRepository repository = givenABatchWithStatuses(statuses);

            int batchStatus = repository.calculateBatchStatusFromDownloads(ANY_BATCH_ID);

            assertThat(batchStatus).isEqualTo(expectedStatus);
        }

        private BatchStatusRepository givenABatchWithStatuses(Integer... statuses) throws Exception {
            DownloadsUriProvider downloadsUriProvider = givenDownloadsUriProvider();

            MockCursorWithStatuses mockCursorWithStatuses = new MockCursorWithStatuses(statuses);

            ContentResolver mockResolver = mock(ContentResolver.class);
            when(mockResolver.query(same(downloadsUriProvider.getAllDownloadsUri()), any(String[].class), anyString(), any(String[].class), anyString()))
                    .thenReturn(mockCursorWithStatuses);

            return new BatchStatusRepository(mockResolver, downloadsUriProvider, mock(SystemFacade.class));
        }
    }

    @RunWith(PowerMockRunner.class)
    @PrepareForTest({ContentUris.class, BatchStatusRepository.class})
    public static class UpdateBatchStatus {

        @Test
        public void whenUpdatingABatchStatusThenTheCorrectBatchIsUpdated() throws Exception {
            final ContentValues mockContentValues = mock(ContentValues.class);
            whenNew(ContentValues.class).withAnyArguments().thenReturn(mockContentValues);
            mockStatic(ContentUris.class);
            when(ContentUris.withAppendedId(BATCHES_URI, ANY_BATCH_ID)).thenReturn(BATCH_BY_ID_URI);

            ContentResolver mockResolver = mock(ContentResolver.class);
            BatchStatusRepository batchStatusRepository = givenBatchStatusServiceAtCurrentTime(mockResolver);

            batchStatusRepository.updateBatchStatus(ANY_BATCH_ID, ANY_BATCH_STATUS);

            verify(mockContentValues).put(Batches.COLUMN_STATUS, ANY_BATCH_STATUS);
            verify(mockContentValues).put(Batches.COLUMN_LAST_MODIFICATION, CURRENT_TIME_MILLIS);
            verify(mockResolver).update(
                    BATCH_BY_ID_URI,
                    mockContentValues,
                    null,
                    null
            );
        }
    }

    @RunWith(PowerMockRunner.class)
    @PrepareForTest({ContentUris.class, BatchStatusRepository.class})
    public static class CancelBatch {

        private ContentValues mockContentValues;
        private ContentResolver mockResolver;
        private BatchStatusRepository batchStatusRepository;

        @Before
        public void setUp() throws Exception {
            mockStatic(ContentUris.class);
            when(ContentUris.withAppendedId(BATCHES_URI, ANY_BATCH_ID)).thenReturn(BATCH_BY_ID_URI);

            mockContentValues = mock(ContentValues.class);
            whenNew(ContentValues.class).withAnyArguments().thenReturn(mockContentValues);

            mockResolver = mock(ContentResolver.class);
            batchStatusRepository = givenBatchStatusServiceAtCurrentTime(mockResolver);
        }

        @Test
        public void whenCancellingBatchItemsThenCorrectDownloadsAreCancelled() throws Exception {
            batchStatusRepository.setBatchItemsCancelled(ANY_BATCH_ID);

            InOrder order = inOrder(mockContentValues, mockResolver);
            thenDownloadsAreCancelled(ANY_BATCH_ID, order);
        }

        @Test
        public void whenCancellingBatchThenCorrectBatchAndDownloadsAreCancelled() throws Exception {
            batchStatusRepository.cancelBatch(ANY_BATCH_ID);

            InOrder order = inOrder(mockContentValues, mockResolver);

            thenDownloadsAreCancelled(ANY_BATCH_ID, order);
            thenStatusIsCancelled(order);
        }

        private void thenDownloadsAreCancelled(long batchId, InOrder inOrder) {
            inOrder.verify(mockContentValues).put(Downloads.COLUMN_STATUS, DownloadStatus.CANCELED);
            inOrder.verify(mockResolver).update(
                    ALL_DOWNLOADS_URI,
                    mockContentValues,
                    Downloads.COLUMN_BATCH_ID + " = ?",
                    new String[]{String.valueOf(batchId)}
            );
        }

        private void thenStatusIsCancelled(InOrder inOrder) {
            inOrder.verify(mockContentValues).put(Batches.COLUMN_STATUS, DownloadStatus.CANCELED);
            inOrder.verify(mockResolver).update(BATCH_BY_ID_URI, mockContentValues, null, null);
        }
    }

    @RunWith(PowerMockRunner.class)
    @PrepareForTest({BatchStatusRepository.class})
    public static class SetBatchItemsFailed {

        @Test
        public void whenSettingBatchItemsToFailedThenCorrectDownloadsAreUpdated() throws Exception {
            final ContentValues mockContentValues = mock(ContentValues.class);
            whenNew(ContentValues.class).withAnyArguments().thenReturn(mockContentValues);

            ContentResolver mockResolver = mock(ContentResolver.class);
            BatchStatusRepository batchStatusRepository = givenBatchStatusServiceAtCurrentTime(mockResolver);

            batchStatusRepository.setBatchItemsFailed(ANY_BATCH_ID, ANY_DOWNLOAD_ID);

            verify(mockContentValues).put(Downloads.COLUMN_STATUS, DownloadStatus.BATCH_FAILED);
            verify(mockResolver).update(
                    ALL_DOWNLOADS_URI,
                    mockContentValues,
                    Downloads.COLUMN_BATCH_ID + " = ? AND " + Downloads._ID + " <> ? ",
                    new String[]{String.valueOf(ANY_BATCH_ID), String.valueOf(ANY_DOWNLOAD_ID)}
            );
        }
    }

    @RunWith(PowerMockRunner.class)
    @PrepareForTest({BatchStatusRepository.class, StringUtils.class})
    public static class UpdateBatchToPendingStatus {

        @Test
        public void whenSettingBatchItemsToFailedThenCorrectDownloadsAreUpdated() throws Exception {
            final ContentValues mockContentValues = mock(ContentValues.class);
            whenNew(ContentValues.class).withAnyArguments().thenReturn(mockContentValues);

            mockStatic(StringUtils.class);
            String where = Batches._ID + " = ?";
            when(StringUtils.join(anyCollection(), anyString())).thenReturn(where);

            ContentResolver mockResolver = mock(ContentResolver.class);
            BatchStatusRepository batchStatusRepository = givenBatchStatusServiceAtCurrentTime(mockResolver);

            batchStatusRepository.updateBatchToPendingStatus(Collections.singletonList(String.valueOf(ANY_BATCH_ID)));

            verify(mockContentValues).put(Batches.COLUMN_STATUS, DownloadStatus.PENDING);
            verify(mockResolver).update(
                    BATCHES_URI,
                    mockContentValues,
                    where,
                    new String[]{String.valueOf(ANY_BATCH_ID)}
            );
        }
    }

    @NonNull
    private static BatchStatusRepository givenBatchStatusServiceAtCurrentTime(final ContentResolver mockResolver) throws Exception {
        DownloadsUriProvider downloadsUriProvider = givenDownloadsUriProvider();
        SystemFacade mockSystemFacade = mock(SystemFacade.class);
        when(mockSystemFacade.currentTimeMillis()).thenReturn(CURRENT_TIME_MILLIS);

        return new BatchStatusRepository(mockResolver, downloadsUriProvider, mockSystemFacade);
    }

    private static DownloadsUriProvider givenDownloadsUriProvider() {
        return new DownloadsUriProvider(
                PUBLICLY_ACCESSIBLE_DOWNLOADS_URI,
                DOWNLOADS_BY_BATCH_URI,
                ALL_DOWNLOADS_URI,
                BATCHES_URI,
                CONTENT_URI,
                DOWNLOADS_WITHOUT_PROGRESS_URI,
                BATCHES_WITHOUT_PROGRESS_URI
        );
    }
}
