package ca.effenti.gameofcards.ui.main;

import android.arch.lifecycle.LifecycleOwner;
import android.arch.lifecycle.Observer;
import android.os.AsyncTask;
import android.support.annotation.Nullable;

import java.io.IOException;
import java.util.List;

import ca.effenti.gameofcards.models.AppDatabaseFactory;
import ca.effenti.gameofcards.models.AppSharedPreferences;
import ca.effenti.gameofcards.models.card.Card;
import ca.effenti.gameofcards.models.card.CardDao;
import ca.effenti.gameofcards.models.card.CardFactory;
import ca.effenti.gameofcards.webservices.DeckOfCardsServiceFactory;
import ca.effenti.gameofcards.webservices.DeckOfCardsService;
import ca.effenti.gameofcards.webservices.deckofcards.CardsResponse;
import ca.effenti.gameofcards.webservices.deckofcards.CreateDeckBody;
import ca.effenti.gameofcards.webservices.deckofcards.DeckResponse;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainPresenterImpl implements MainPresenter, Observer<Card> {
    private MainView view;
    private LifecycleOwner lifecycleOwner;
    private DeckOfCardsService deckService;
    private CardDao cardDao;

    private String deckId;

    public MainPresenterImpl(MainView mainView, LifecycleOwner lifecycleOwner){
        this.view = mainView;
        this.lifecycleOwner = lifecycleOwner;

        // Get service and database
        // Ideally, these would be injected using a dependency injector
        this.deckService = DeckOfCardsServiceFactory.getService();
        this.cardDao = AppDatabaseFactory.getDatabase().cardDao();

        // Try to restore deck id from shared preferences
        this.setDeckId(AppSharedPreferences.getDeckId());
        if(this.deckId == null){
            this.initializeDeck();
        }
    }

    private void setDeckId(String deckId) {
        this.deckId = deckId;
        // Persist the new value
        AppSharedPreferences.setDeckId(deckId);
        // Tell the view to enable or disable button
        this.view.enableDrawButton(this.deckId != null);
    }

    private void initializeDeck() {
        // Do an asynchronous call to get a new deck
        this.deckService.createDeck(new CreateDeckBody(1)).enqueue(new Callback<DeckResponse>() {
            @Override
            public void onResponse(Call<DeckResponse> call, Response<DeckResponse> response) {
                setDeckId(response.body().getDeckId());
            }

            @Override
            public void onFailure(Call<DeckResponse> call, Throwable t) {}
        });
    }

    @Override
    public void onResume() {
        // Reevaluate the state of our button
        this.view.enableDrawButton(this.deckId != null);

        // Connect to our database
        this.cardDao.observeLast().observe(lifecycleOwner, this);
    }

    @Override
    public void onPause() {
        this.cardDao.observeLast().removeObserver(this);
    }

    @Override
    public void drawACard() {
        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    Response<CardsResponse> response = deckService.drawCards(deckId, 1).execute();
                    // Save the cards
                    List<Card> cardList = CardFactory.fromCardsResponse(response.body());
                    cardDao.insert(cardList.get(0));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    @Override
    public void onChanged(@Nullable Card card) {
        if (card != null) {
            this.view.showCardImage(card.getImage());
        }
    }
}