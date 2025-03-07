package com.example.androidcicd;

import com.example.androidcicd.movie.Movie;
import com.example.androidcicd.movie.MovieProvider;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class MovieProviderTest {
    @Mock
    private FirebaseFirestore mockFirestore;

    @Mock
    private CollectionReference mockMovieCollection;

    @Mock
    private DocumentReference mockDocRef;

    @Mock
    private Query mockQuery;

    @Mock
    private Task<QuerySnapshot> mockQueryTask;

    @Mock
    private QuerySnapshot mockQuerySnapshot;

    @Mock
    private Task<Void> mockVoidTask;

    private MovieProvider movieProvider;

    @Before
    public void setUp() {
        // Start up mocks
        MockitoAnnotations.openMocks(this);
        // Define the behaviour we want during our tests. This part is what avoids the calls to firestore.
        when(mockFirestore.collection("movies")).thenReturn(mockMovieCollection);
        when(mockMovieCollection.document()).thenReturn(mockDocRef);
        when(mockMovieCollection.document(anyString())).thenReturn(mockDocRef);
        when(mockMovieCollection.whereEqualTo(anyString(), anyString())).thenReturn(mockQuery);
        when(mockQuery.get()).thenReturn(mockQueryTask);
        when(mockDocRef.set(any(Movie.class))).thenReturn(mockVoidTask);

        // Setup the movie provider
        MovieProvider.setInstanceForTesting(mockFirestore);
        movieProvider = MovieProvider.getInstance(mockFirestore);
    }

    @Test
    public void testAddMovieSetsId() {
        // Movie to add
        Movie movie = new Movie("Oppenheimer", "Thriller/Historical Drama", 2023);

        // Define the ID we want to set for the movie
        when(mockDocRef.getId()).thenReturn("123");

        // Setup empty query result for title uniqueness check
        List<DocumentReference> emptyList = new ArrayList<>();
        when(mockQueryTask.isSuccessful()).thenReturn(true);
        when(mockQueryTask.getResult()).thenReturn(mockQuerySnapshot);
        when(mockQuerySnapshot.iterator()).thenReturn(emptyList.iterator());
        when(mockVoidTask.addOnSuccessListener(any())).thenReturn(mockVoidTask);
        when(mockVoidTask.addOnFailureListener(any())).thenReturn(mockVoidTask);

        // Setup CountDownLatch to wait for async operation
        CountDownLatch latch = new CountDownLatch(1);
        final boolean[] success = {false};

        // Add movie
        movieProvider.addMovie(movie, new MovieProvider.MovieOperationCallback() {
            @Override
            public void onSuccess() {
                success[0] = true;
                latch.countDown();
            }

            @Override
            public void onError(String errorMessage) {
                success[0] = false;
                latch.countDown();
            }
        });

        try {
            latch.await(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        assertEquals("Movie was not updated with correct id.", "123", movie.getId());
        assertEquals(true, success[0]);
    }

    @Test
    public void testDeleteMovie() {
        // Create movie and set our id
        Movie movie = new Movie("Oppenheimer", "Thriller/Historical Drama", 2023);
        movie.setId("123");

        // Call the delete movie and verify the firebase delete method was called.
        movieProvider.deleteMovie(movie);
        verify(mockDocRef).delete();
    }

    @Test
    public void testAddMovieWithDuplicateTitle() {
        // Setup mock for duplicate title
        Movie movie = new Movie("Oppenheimer", "Thriller/Historical Drama", 2023);

        // Create a mock document that will be returned in the query
        List<DocumentReference> documentList = new ArrayList<>();
        DocumentReference existingDoc = mock(DocumentReference.class);
        documentList.add(existingDoc);

        when(mockQueryTask.isSuccessful()).thenReturn(true);
        when(mockQueryTask.getResult()).thenReturn(mockQuerySnapshot);
        when(mockQuerySnapshot.isEmpty()).thenReturn(false);
        when(mockQuerySnapshot.iterator()).thenReturn(documentList.iterator());

        // Setup CountDownLatch to wait for async operation
        CountDownLatch latch = new CountDownLatch(1);
        final String[] errorMessage = {null};

        // Attempt to add movie with duplicate title
        movieProvider.addMovie(movie, new MovieProvider.MovieOperationCallback() {
            @Override
            public void onSuccess() {
                latch.countDown();
            }

            @Override
            public void onError(String message) {
                errorMessage[0] = message;
                latch.countDown();
            }
        });

        try {
            latch.await(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        assertEquals("A movie with this title already exists", errorMessage[0]);
    }
}