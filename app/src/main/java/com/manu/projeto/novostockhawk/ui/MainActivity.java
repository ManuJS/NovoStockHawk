package com.manu.projeto.novostockhawk.ui;

import android.app.LoaderManager;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.Loader;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.text.InputType;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.afollestad.materialdialogs.MaterialDialog;
import com.google.android.gms.gcm.GcmNetworkManager;
import com.google.android.gms.gcm.Task;
import com.manu.projeto.novostockhawk.R;
import com.manu.projeto.novostockhawk.SimpleItemTouchHelperCallback;
import com.manu.projeto.novostockhawk.StockTaskService;
import com.manu.projeto.novostockhawk.Utils;
import com.manu.projeto.novostockhawk.data.QuoteColumns;
import com.manu.projeto.novostockhawk.data.QuoteProvider;
import com.melnykov.fab.FloatingActionButton;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;

import com.google.android.gms.gcm.PeriodicTask;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;


public class MainActivity extends AppCompatActivity implements LoaderManager.LoaderCallbacks<Cursor> {


    public static String BROADCAST_NO_STOCK_FOUND;

    private CharSequence mTitle;
    private Intent mServiceIntent;
    private static final int CURSOR_LOADER_ID = 0;
    private Context mContext;
    private Cursor mCursor;
    boolean isConnected;
    private QuoteCursorAdapter mCursorAdapter;

    private ItemTouchHelper mItemTouchHelper;

    private NoStockFoundReceiver mReceiver;
    private boolean mReceiverRegistered;

    private TextView mTextViewConnection;
    private ProgressDialog mProgressDialog;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mContext = this;

        BROADCAST_NO_STOCK_FOUND = getString(R.string.broadcast_stock_not_found);

        if(!mReceiverRegistered)
        {
            mReceiver = new NoStockFoundReceiver();
            LocalBroadcastManager.getInstance(this).registerReceiver(mReceiver, new IntentFilter(BROADCAST_NO_STOCK_FOUND));
            mReceiverRegistered = true;
        }
//pega o contexto de conexao do gadget
        ConnectivityManager cm =
                (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);

        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        isConnected = activeNetwork != null &&
                activeNetwork.isConnectedOrConnecting();
        setContentView(R.layout.activity_main);
        // The intent service is for executing immediate pulls from the Yahoo API
        // GCMTaskService can only schedule tasks, they cannot execute immediately
        mServiceIntent = new Intent(this, MainActivity.class);
        if (savedInstanceState == null)
        {
            // Run the initialize task service so that some stocks appear upon an empty database
            mServiceIntent.putExtra(getString(R.string.m_tag), getString(R.string.m_init));
            if (isConnected)
            {
                startService(mServiceIntent);
            }
        }
        RecyclerView recyclerView = (RecyclerView) findViewById(R.id.recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        getLoaderManager().initLoader(CURSOR_LOADER_ID, null, this);

        mCursorAdapter = new QuoteCursorAdapter(this, null);
        recyclerView.addOnItemTouchListener(new RecyclerViewItemClickListener(this,
                new RecyclerViewItemClickListener.OnItemClickListener() {
                    @Override
                    public void onItemClick(View v, int position) {
                        mCursor.moveToPosition(position);
                        new HistoricalDataAsyncTask(mCursor.getString(mCursor.getColumnIndex(getString(R.string.symbol)))).execute();
                    }
                }));
        recyclerView.setAdapter(mCursorAdapter);

        mTextViewConnection = (TextView) findViewById(R.id.tv_connection);
        mTextViewConnection.setVisibility(isConnected ? View.GONE : View.VISIBLE);

        recyclerView.setVisibility(isConnected ? View.VISIBLE : View.GONE);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.attachToRecyclerView(recyclerView);

        fab.setVisibility(isConnected ? View.VISIBLE : View.GONE);

        fab.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                new MaterialDialog.Builder(mContext).title(R.string.symbol_search)
                        .content(R.string.content_test)
                        .inputType(InputType.TYPE_CLASS_TEXT)
                        .input(R.string.input_hint, R.string.input_prefill, new MaterialDialog.InputCallback() {
                            @Override public void onInput(MaterialDialog dialog, CharSequence input) {
                                // On FAB click, receive user input. Make sure the stock doesn't already exist
                                // in the DB and proceed accordingly
                                Cursor c = getContentResolver().query(QuoteProvider.Quotes.CONTENT_URI,
                                        new String[] { QuoteColumns.SYMBOL }, QuoteColumns.SYMBOL + "= ?",
                                        new String[] { input.toString() }, null);
                                if (c.getCount() != 0) {
                                    Toast toast =
                                            Toast.makeText(MainActivity.this, getString(R.string.stock_already_saved),
                                                    Toast.LENGTH_LONG);
                                    toast.setGravity(Gravity.CENTER, Gravity.CENTER, 0);
                                    toast.show();
                                    return;
                                } else {
                                    // Add the stock to DB
                                    mServiceIntent.putExtra(getString(R.string.m_tag), getString(R.string.m_add));
                                    mServiceIntent.putExtra(getString(R.string.symbol), input.toString());
                                    startService(mServiceIntent);
                                }
                            }
                        })
                        .show();
            }
        });

        ItemTouchHelper.Callback callback = new SimpleItemTouchHelperCallback(mCursorAdapter);
        mItemTouchHelper = new ItemTouchHelper(callback);
        mItemTouchHelper.attachToRecyclerView(recyclerView);

        mTitle = getTitle();
        if (isConnected){
            long period = 3600L;
            long flex = 10L;
            String periodicTag = getString(R.string.m_periodic);

            // create a periodic task to pull stocks once every hour after the app has been opened. This
            // is so Widget data stays up to date.
            PeriodicTask periodicTask = new PeriodicTask.Builder()
                    .setService(StockTaskService.class)
                    .setPeriod(period)
                    .setFlex(flex)
                    .setTag(periodicTag)
                    .setRequiredNetwork(Task.NETWORK_STATE_CONNECTED)
                    .setRequiresCharging(false)
                    .build();
            // Schedule task with tag "periodic." This ensure that only the stocks present in the DB
            // are updated.
            GcmNetworkManager.getInstance(this).schedule(periodicTask);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        if(mReceiverRegistered)
        {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(mReceiver);
            mReceiverRegistered = false;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        getLoaderManager().restartLoader(CURSOR_LOADER_ID, null, this);
    }

    public void networkToast(){
        Toast.makeText(mContext, getString(R.string.network_toast), Toast.LENGTH_SHORT).show();
    }

    public void restoreActionBar() {
        ActionBar actionBar = getSupportActionBar();
        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
        actionBar.setDisplayShowTitleEnabled(true);
        actionBar.setTitle(mTitle);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.my_stocks, menu);
        restoreActionBar();
        return true;
    }

//    @Override
//    public boolean onOptionsItemSelected(MenuItem item) {
//        // Handle action bar item clicks here. The action bar will
//        // automatically handle clicks on the Home/Up button, so long
//        // as you specify a parent activity in AndroidManifest.xml.
//        int id = item.getItemId();
//
//        //noinspection SimplifiableIfStatement
//        if (id == R.id.action_settings) {
//            return true;
//        }
//
//        if (id == R.id.action_change_units){
//            // this is for changing stock changes from percent value to dollar value
//            Utils.showPercent = !Utils.showPercent;
//            this.getContentResolver().notifyChange(QuoteProvider.Quotes.CONTENT_URI, null);
//        }
//
//        return super.onOptionsItemSelected(item);
//    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args){
        // This narrows the return to only the stocks that are most current.
        return new CursorLoader(this, QuoteProvider.Quotes.CONTENT_URI,
                new String[]{ QuoteColumns._ID, QuoteColumns.SYMBOL, QuoteColumns.BIDPRICE,
                        QuoteColumns.PERCENT_CHANGE, QuoteColumns.CHANGE, QuoteColumns.ISUP},
                QuoteColumns.ISCURRENT + " = ?",
                new String[]{"1"},
                null);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data){
        mCursorAdapter.swapCursor(data);
        mCursor = data;
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader){
        mCursorAdapter.swapCursor(null);
    }

    private class NoStockFoundReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {

            String stockName = intent.hasExtra(getString(R.string.m_name)) ? "\"" + intent.getStringExtra(getString(R.string.m_name)) + "\"" : getString(R.string.with_this_name);
            Toast.makeText(MainActivity.this, getString(R.string.no_stock_found).replace(getString(R.string.m_string_placeholder), stockName), Toast.LENGTH_LONG).show();
        }
    }

    private class HistoricalDataAsyncTask extends AsyncTask<Void, Void, ArrayList<JSONObject>> {

        private String mSymbol;
        private String mStringResponse;
        ArrayList<JSONObject> mContentVals;

        public HistoricalDataAsyncTask(String symbol){
            mSymbol = symbol;
        }

        @Override
        protected void onPostExecute(ArrayList<JSONObject> contentVals) {
            super.onPostExecute(contentVals);

            if(mProgressDialog != null && mProgressDialog.isShowing())
                mProgressDialog.dismiss();

            ArrayList<String> endValues = new ArrayList<>();
            ArrayList<String> dates = new ArrayList<>();

            for(JSONObject jsonObject : mContentVals)
            {
                try
                {
                    endValues.add(jsonObject.getString(getString(R.string.m_close)));
                    dates.add(jsonObject.getString(getString(R.string.m_date)));
                }
                catch (JSONException e)
                {
                    e.printStackTrace();
                }
            }

            Intent intent = new Intent(MainActivity.this, null);
            intent.putStringArrayListExtra(getString(R.string.m_end_values), endValues);
            intent.putStringArrayListExtra(getString(R.string.m_dates), dates);
            startActivity(intent);
        }

        @Override
        protected ArrayList<JSONObject> doInBackground(Void... params) {

            Date now = new Date();

            Calendar c = Calendar.getInstance();
            c.setTime(now);
            c.add(Calendar.MONTH, -6);

            SimpleDateFormat sdf = new SimpleDateFormat(getString(R.string.default_date_format));

            OkHttpClient client = new OkHttpClient();
            Request request = new Request.Builder()
                    .url(getString(R.string.url_yahoo_finance_1) + "'" +
                            mSymbol + "'" + getString(R.string.url_yahoo_finance_2) + "'" +
                            sdf.format(c.getTime()) + "'" + getString(R.string.url_yahoo_finance_3) + "'" +
                            sdf.format(now) + "'" + getString(R.string.url_yahoo_finance_4))
                    .build();

            Response response;

            try
            {
                response = client.newCall(request).execute();
                mStringResponse = response.body().string();
                mContentVals = Utils.quoteJsonToContentVals(MainActivity.this, mStringResponse, true);
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }

            return mContentVals;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();

            mProgressDialog = ProgressDialog.show(MainActivity.this, getString(R.string.please_wait),
                    getString(R.string.loading_data), true);
        }
    }
}