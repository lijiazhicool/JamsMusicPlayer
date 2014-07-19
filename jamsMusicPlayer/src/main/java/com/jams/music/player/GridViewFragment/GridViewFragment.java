package com.jams.music.player.GridViewFragment;

import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.LinearInterpolator;
import android.view.animation.TranslateAnimation;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.andraskindler.quickscroll.QuickScrollGridView;
import com.jams.music.player.Animations.FadeAnimation;
import com.jams.music.player.DBHelpers.DBAccessHelper;
import com.jams.music.player.Helpers.ImageViewCoordHelper;
import com.jams.music.player.Helpers.PauseOnScrollHelper;
import com.jams.music.player.Helpers.TypefaceHelper;
import com.jams.music.player.Helpers.UIElementsHelper;
import com.jams.music.player.BrowserInnerSubFragment.BrowserInnerSubFragment;
import com.jams.music.player.MainActivity.MainActivity;
import com.jams.music.player.R;
import com.jams.music.player.Utils.Common;
import com.nhaarman.listviewanimations.swinginadapters.prepared.SwingBottomInAnimationAdapter;

import java.util.HashMap;

/**
 * Generic, multipurpose GridView fragment.
 * 
 * @author Saravan Pantham
 */
public class GridViewFragment extends Fragment {
	
	private Context mContext;
	private GridViewFragment mFragment;
	private Common mApp;
	private View mRootView;
    private RelativeLayout mActionBarBleed;
    private RelativeLayout mGridViewContainer;
    private TextView mHeaderText;
    private TextView mPlayAllText;
	private int mFragmentId;
	
	private QuickScrollGridView mQuickScroll;
	private BaseAdapter mGridViewAdapter;
	private HashMap<Integer, String> mDBColumnsMap;
	private GridView mGridView;
	private TextView mEmptyTextView;
	
	public Handler mHandler = new Handler();
	private Cursor mCursor;
	private String mQuerySelection = "";
	
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mRootView = inflater.inflate(R.layout.fragment_grid_view, container, false);
        mContext = getActivity().getApplicationContext();
	    mApp = (Common) mContext;
        mFragment = this;

        //Set the background color and the partial color bleed.
        mRootView.setBackgroundColor(UIElementsHelper.getBackgroundColor(mContext));
        mActionBarBleed = (RelativeLayout) mRootView.findViewById(R.id.actionbar_bleed);

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN)
            mActionBarBleed.setBackgroundDrawable(UIElementsHelper.getGeneralActionBarBackground(mContext));
        else
            mActionBarBleed.setBackground(UIElementsHelper.getGeneralActionBarBackground(mContext));

        //Set the header text.
        mHeaderText = (TextView) mRootView.findViewById(R.id.fragment_grid_view_header_text);
        mHeaderText.setTypeface(TypefaceHelper.getTypeface(mContext, "Roboto-Regular"));
        mHeaderText.setText(getArguments().getString(MainActivity.FRAGMENT_HEADER));

        /* Style the "PLAY ALL" text.
        *
        * NOTE: The "PLAY ALL" text should be replaced by a floating action
        * button in Android L (API 21). */
        mPlayAllText = (TextView) mRootView.findViewById(R.id.fragment_grid_view_play_all);
        mPlayAllText.setTypeface(TypefaceHelper.getTypeface(mContext, "Roboto-Medium"));

        //Grab the fragment. This will determine which data to load into the cursor.
        mFragmentId = getArguments().getInt(Common.FRAGMENT_ID);
        mDBColumnsMap = new HashMap<Integer, String>();
	    
        mQuickScroll = (QuickScrollGridView) mRootView.findViewById(R.id.quickscrollgrid);

		//Set the adapter for the outer gridview.
        mGridView = (GridView) mRootView.findViewById(R.id.generalGridView);
        mGridViewContainer = (RelativeLayout) mRootView.findViewById(R.id.fragment_grid_view_frontal_layout);
        mGridView.setVerticalScrollBarEnabled(false);

        //Set the number of gridview columns based on the screen density and orientation.
        if (mApp.isPhoneInLandscape() || mApp.isTabletInLandscape()) {
            mGridView.setNumColumns(4);
        } else if (mApp.isPhoneInPortrait()) {
            mGridView.setNumColumns(2);
        } else if (mApp.isTabletInPortrait()) {
            mGridView.setNumColumns(3);
        }

        //KitKat translucent navigation/status bar.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
        	int topPadding = Common.getStatusBarHeight(mContext);
            
            //Calculate navigation bar height.
            int navigationBarHeight = 0;
            int resourceId = getResources().getIdentifier("navigation_bar_height", "dimen", "android");
            if (resourceId > 0) {
                navigationBarHeight = getResources().getDimensionPixelSize(resourceId);
            }
            
            mGridView.setClipToPadding(false);
            mGridView.setPadding(0, 0, 0, navigationBarHeight);
            mHeaderText.setPadding(0, topPadding, 0, 0);
            mPlayAllText.setPadding(0, topPadding, 0, 0);
            mQuickScroll.setPadding(0, 0, 0, navigationBarHeight);
            
        }

        //Set the empty views.
        mEmptyTextView = (TextView) mRootView.findViewById(R.id.empty_view_text);
	    mEmptyTextView.setTypeface(TypefaceHelper.getTypeface(mContext, "Roboto-Light"));
	    mEmptyTextView.setPaintFlags(mEmptyTextView.getPaintFlags() | Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        
        //Create a set of options to optimize the bitmap memory usage.
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        options.inJustDecodeBounds = false;
        options.inPurgeable = true;
	    
        mHandler.postDelayed(queryRunnable, 250);
        return mRootView;
    }
    
    /**
     * Query runnable.
     */
    public Runnable queryRunnable = new Runnable() {

		@Override
		public void run() {
			new AsyncRunQuery().execute();
			
		}
    	
    };

    /**
     * Click listener for the "PLAY ALL" text.
     */
    private View.OnClickListener playAllClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {

        }

    };
    
    /**
     * Item click listener for the GridView/ListView.
     */
    private OnItemClickListener onItemClickListener = new OnItemClickListener() {

		@Override
		public void onItemClick(AdapterView<?> arg0, View view, int index, long id) {

            //Grab a handle on the album art ImageView within the grid.
            ImageView albumArt = (ImageView) view.findViewById(R.id.artistsGridViewImage);

            //Grab the album art image data.
            int[] screenLocation = new int[2];
            albumArt.getLocationOnScreen(screenLocation);
            String albumArtPath = (String) view.getTag(R.string.album_art);
            Bitmap thumbnail = ((BitmapDrawable) albumArt.getDrawable()).getBitmap();
            ImageViewCoordHelper info = new ImageViewCoordHelper(albumArtPath, thumbnail);

            //Pass on the album art view info to the new fragment.
            Bundle bundle = new Bundle();
            int orientation = getResources().getConfiguration().orientation;

            bundle.putInt("orientation", orientation);
            bundle.putString("albumArtPath", info.mAlbumArtPath);
            bundle.putInt("left", screenLocation[0]);
            bundle.putInt("top", screenLocation[1]);
            bundle.putInt("width", albumArt.getWidth());
            bundle.putInt("height", albumArt.getHeight());

            //Fire up the new fragment.
            BrowserInnerSubFragment fragment = new BrowserInnerSubFragment();
            fragment.setArguments(bundle);

            FragmentManager manager = getActivity().getSupportFragmentManager();
            FragmentTransaction transaction = manager.beginTransaction();
            transaction.add(R.id.mainActivityContainer2, fragment, "horizListSubFragment")
                       .addToBackStack("horizListSubFragment")
                       .commit();
			
		}
    	
    };
    
    @Override
    public void onDestroyView() {
    	super.onDestroyView();
    	mRootView = null;
    	
    	if (mCursor!=null) {
        	mCursor.close();
        	mCursor = null;
    	}
    	
    	onItemClickListener = null;
    	mGridView = null;
    	mGridViewAdapter = null;
    	mContext = null;
    	mHandler = null;
    	
    }
    
    /**
     * Runs the correct DB query based on the passed in fragment id and 
     * displays the GridView.
     * 
     * @author Saravan Pantham
     */
    public class AsyncRunQuery extends AsyncTask<Void, Void, Void> {

		@Override
		protected Void doInBackground(Void... params) {
	        mCursor = mApp.getDBAccessHelper().getFragmentCursor(mContext, mQuerySelection, mFragmentId);
	        loadDBColumnNames();
	        
	        return null;
		}
		
		/**
		 * Populates the DB column names based on the specifed fragment id.
		 */
		private void loadDBColumnNames() {
			
			switch (mFragmentId) {
			case Common.ARTISTS_FRAGMENT:
				mDBColumnsMap.put(GridViewCardsAdapter.TITLE_TEXT, DBAccessHelper.SONG_ARTIST);
				mDBColumnsMap.put(GridViewCardsAdapter.SOURCE, DBAccessHelper.SONG_SOURCE);
				mDBColumnsMap.put(GridViewCardsAdapter.FILE_PATH, DBAccessHelper.SONG_FILE_PATH);
				mDBColumnsMap.put(GridViewCardsAdapter.ARTWORK_PATH, DBAccessHelper.SONG_ALBUM_ART_PATH);
				break;
			case Common.ALBUM_ARTISTS_FRAGMENT:
				mDBColumnsMap.put(GridViewCardsAdapter.TITLE_TEXT, DBAccessHelper.SONG_ALBUM_ARTIST);
				mDBColumnsMap.put(GridViewCardsAdapter.SOURCE, DBAccessHelper.SONG_SOURCE);
				mDBColumnsMap.put(GridViewCardsAdapter.FILE_PATH, DBAccessHelper.SONG_FILE_PATH);
				mDBColumnsMap.put(GridViewCardsAdapter.ARTWORK_PATH, DBAccessHelper.SONG_ALBUM_ART_PATH);
				break;
			case Common.ALBUMS_FRAGMENT:
				mDBColumnsMap.put(GridViewCardsAdapter.TITLE_TEXT, DBAccessHelper.SONG_ALBUM);
				mDBColumnsMap.put(GridViewCardsAdapter.SOURCE, DBAccessHelper.SONG_SOURCE);
				mDBColumnsMap.put(GridViewCardsAdapter.FILE_PATH, DBAccessHelper.SONG_FILE_PATH);
				mDBColumnsMap.put(GridViewCardsAdapter.ARTWORK_PATH, DBAccessHelper.SONG_ALBUM_ART_PATH);
                mDBColumnsMap.put(GridViewCardsAdapter.FIELD_1, DBAccessHelper.SONG_ARTIST);
				break;
			case Common.PLAYLISTS_FRAGMENT:
				break;
			case Common.GENRES_FRAGMENT:
				break;
			case Common.FOLDERS_FRAGMENT:
				break;
			}
			
		}
    	
		@Override
		public void onPostExecute(Void result) {
			super.onPostExecute(result);
            mHandler.postDelayed(initGridView, 200);
			
		}
		
    }

    /**
     * Runnable that loads the GridView after a set interval.
     */
    private Runnable initGridView = new Runnable() {

        @Override
        public void run() {
            TranslateAnimation animation = new TranslateAnimation(Animation.RELATIVE_TO_SELF, 0.0f,
					  											  Animation.RELATIVE_TO_SELF, 0.0f,
					  											  Animation.RELATIVE_TO_SELF, 2.0f,
					  											  Animation.RELATIVE_TO_SELF, 0.0f);

			animation.setDuration(150);
			animation.setInterpolator(new AccelerateDecelerateInterpolator());

            mGridViewAdapter = new GridViewCardsAdapter(mContext, mFragment, mDBColumnsMap);
            //mGridView.setAdapter(mGridViewAdapter);

            //GridView animation adapter.
            final SwingBottomInAnimationAdapter animationAdapter = new SwingBottomInAnimationAdapter(mGridViewAdapter, 100, 150);
            animationAdapter.setShouldAnimate(true);
            animationAdapter.setShouldAnimateFromPosition(0);
            animationAdapter.setAbsListView(mGridView);
            mGridView.setAdapter(animationAdapter);

            //Init the quick scroll widget.
            mQuickScroll.init(QuickScrollGridView.TYPE_INDICATOR_WITH_HANDLE,
                              mGridView,
                              (GridViewCardsAdapter) mGridViewAdapter,
                              QuickScrollGridView.STYLE_HOLO);

            int[] quickScrollColors = UIElementsHelper.getQuickScrollColors(mContext);
            PauseOnScrollHelper scrollHelper = new PauseOnScrollHelper(mApp.getPicasso(), null, false, true);

            mQuickScroll.setOnScrollListener(scrollHelper);
            mQuickScroll.setPicassoInstance(mApp.getPicasso());
            mQuickScroll.setHandlebarColor(quickScrollColors[0], quickScrollColors[0], quickScrollColors[1]);
            mQuickScroll.setIndicatorColor(quickScrollColors[1], quickScrollColors[0], quickScrollColors[2]);
            mQuickScroll.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 48);

	        animation.setAnimationListener(new AnimationListener() {

				@Override
				public void onAnimationEnd(Animation arg0) {
					mQuickScroll.setVisibility(View.VISIBLE);
                    //animationAdapter.setShouldAnimate(false);

				}

				@Override
				public void onAnimationRepeat(Animation arg0) {
					// TODO Auto-generated method stub

				}

				@Override
				public void onAnimationStart(Animation arg0) {
					mGridView.setVisibility(View.VISIBLE);

				}

	        });

	        mGridView.startAnimation(animation);
        }

    };

    /*
     * Getter methods.
     */

	public GridViewCardsAdapter getGridViewAdapter() {
		return (GridViewCardsAdapter) mGridViewAdapter;
	}

	public GridView getGridView() {
		return mGridView;
	}

	public Cursor getCursor() {
		return mCursor;
	}

	/*
	 * Setter methods.
	 */
	
	public void setCursor(Cursor cursor) {
		this.mCursor = cursor;
	}

}
