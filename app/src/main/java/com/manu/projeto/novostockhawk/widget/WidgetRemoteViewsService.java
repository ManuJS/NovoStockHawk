package com.manu.projeto.novostockhawk.widget;

import android.annotation.TargetApi;
import android.content.Intent;
import android.database.Cursor;
import android.os.Binder;
import android.os.Build;
import android.widget.AdapterView;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import com.manu.projeto.novostockhawk.R;
import com.manu.projeto.novostockhawk.data.QuoteColumns;
import com.manu.projeto.novostockhawk.data.QuoteProvider;
import com.manu.projeto.novostockhawk.ui.MainActivity;


@TargetApi(Build.VERSION_CODES.HONEYCOMB)
public class WidgetRemoteViewsService extends RemoteViewsService {

    private final static String[] QUOTE_COLUMNS = {
            QuoteColumns.SYMBOL,
            QuoteColumns._ID,
            QuoteColumns.BIDPRICE,
            QuoteColumns.CHANGE,
            QuoteColumns.PERCENT_CHANGE,
            QuoteColumns.CREATED,
            QuoteColumns.ISCURRENT,
            QuoteColumns.ISUP
    };

    private final static int INDEX_SYMBOL = 0;
    private final static int INDEX_ID = 1;
    private final static int INDEX_BIDPRICE = 2;
    private final static int INDEX_CHANGE = 3;
    private final static int INDEX_PERCENT_CHANGE = 4;
    private final static int INDEX_CREATED = 5;
    private final static int INDEX_ISCURRENT = 6;
    private final static int INDEX_ISUP = 7;

    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new RemoteViewsFactory() {
            private Cursor data = null;

            @Override
            public void onCreate() {

            }

            @Override
            public void onDataSetChanged() {
                if (data != null) {
                    data.close();
                }

                final long identityToken = Binder.clearCallingIdentity();
                data = queryData();
                Binder.restoreCallingIdentity(identityToken);
            }

            @Override
            public void onDestroy() {
                if (data != null) {
                    data.close();
                    data = null;
                }
            }

            @Override
            public int getCount() {
                return data == null ? 0 : data.getCount();
            }

            @Override
            public RemoteViews getViewAt(int position) {
                if (position == AdapterView.INVALID_POSITION ||
                        data == null || !data.moveToPosition(position)) {
                    return null;
                }
                RemoteViews views = new RemoteViews(getPackageName(),
                        R.layout.widget_large);


                String symbol = data.getString(INDEX_SYMBOL).toUpperCase();
                String valuePercent = data.getString(INDEX_PERCENT_CHANGE);
                String bidPrice = data.getString(INDEX_BIDPRICE);
                String created = data.getString(INDEX_CREATED);
                boolean isUp = data.getInt(INDEX_ISUP) == 1;

                data.close();

                views.setTextViewText(R.id.widget_symbol, symbol);
                views.setTextViewText(R.id.widget_bidprice, bidPrice);
                views.setTextViewText(R.id.widget_value_percent, valuePercent);


                if (isUp) {
                    views.setInt(R.id.widget_value_percent, "setBackgroundResource",
                            R.drawable.percent_change_pill_green);
                } else {
                    views.setInt(R.id.widget_value_percent, "setBackgroundResource",
                            R.drawable.percent_change_pill_red);
                }

                // Create intent to launch activity on click
                Intent launchIntent = new Intent(WidgetRemoteViewsService.this, MainActivity.class);
                views.setOnClickFillInIntent(R.id.widget, launchIntent);

                return views;
            }

            @Override
            public RemoteViews getLoadingView() {
                return new RemoteViews(getPackageName(), R.layout.widget_large);
            }

            @Override
            public int getViewTypeCount() {
                return 1;
            }

            @Override
            public long getItemId(int position) {
                if (data != null) {
                    data.close();
                }

                final long identityToken = Binder.clearCallingIdentity();
                data = queryData();
                Binder.restoreCallingIdentity(identityToken);



                if (data.moveToPosition(position))
                    return data.getLong(INDEX_ID);
                return position;
            }

            @Override
            public boolean hasStableIds() {
                return true;
            }
        };
    }


    public Cursor queryData() {
        return getContentResolver().query(
                QuoteProvider.Quotes.CONTENT_URI,
                QUOTE_COLUMNS,
                QuoteColumns.SYMBOL + " IS NOT NULL) GROUP BY (" + QuoteColumns.SYMBOL,
                null,
                QuoteColumns._ID + " ASC");
    }

}
