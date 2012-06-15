package de.podfetcher.storage;

import java.util.ArrayList;
import java.io.File;
import java.util.concurrent.Callable;

import de.podfetcher.feed.*;
import de.podfetcher.service.DownloadService;
import de.podfetcher.util.NumberGenerator;
import de.podfetcher.R;

import android.util.Log;
import android.database.Cursor;
import android.app.DownloadManager;
import android.content.Context;
import android.net.Uri;
import android.os.Messenger;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.content.ComponentName;
import android.os.Message;
import android.os.RemoteException;
import android.content.Intent;
import android.webkit.URLUtil;

public class DownloadRequester {
	private static final String TAG = "DownloadRequester";
	private static final int currentApi = android.os.Build.VERSION.SDK_INT;

	public static String EXTRA_DOWNLOAD_ID = "extra.de.podfetcher.storage.download_id";
	public static String EXTRA_ITEM_ID = "extra.de.podfetcher.storage.item_id";

	public static String ACTION_FEED_DOWNLOAD_COMPLETED = "action.de.podfetcher.storage.feed_download_completed";
	public static String ACTION_MEDIA_DOWNLOAD_COMPLETED = "action.de.podfetcher.storage.media_download_completed";
	public static String ACTION_IMAGE_DOWNLOAD_COMPLETED = "action.de.podfetcher.storage.image_download_completed";
	
	private static boolean STORE_ON_SD = true;
	public static String IMAGE_DOWNLOADPATH = "images/";
	public static String FEED_DOWNLOADPATH = "cache/";
	public static String MEDIA_DOWNLOADPATH = "media/";

	private static DownloadRequester downloader;
	private DownloadManager manager;

	public ArrayList<FeedFile> feeds;
	public ArrayList<FeedFile> images;
	public ArrayList<FeedFile> media;

	private DownloadRequester() {
		feeds = new ArrayList<FeedFile>();
		images = new ArrayList<FeedFile>();
		media = new ArrayList<FeedFile>();

	}

	public static DownloadRequester getInstance() {
		if (downloader == null) {
			downloader = new DownloadRequester();
		}
		return downloader;
	}

	private long download(Context context, ArrayList<FeedFile> type,
			FeedFile item, File dest) {
		if (dest.exists()) {
			Log.d(TAG, "File already exists. Deleting !");
			dest.delete();
		}
		Log.d(TAG, "Requesting download of url " + item.getDownload_url());
		type.add(item);
		DownloadManager.Request request = new DownloadManager.Request(
				Uri.parse(item.getDownload_url()))
		.setDestinationUri(Uri.fromFile(dest));
		Log.d(TAG, "Version is " + currentApi);
		if (currentApi >= 11) {
			request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_HIDDEN);
		} else {
			request.setVisibleInDownloadsUi(false);
			request.setShowRunningNotification(false);
		}
		
		// TODO Set Allowed Network Types
		DownloadManager manager = (DownloadManager) context
				.getSystemService(Context.DOWNLOAD_SERVICE);
		context.startService(new Intent(context, DownloadService.class));
		long downloadId = manager.enqueue(request);
		item.setDownloadId(downloadId);
		item.setFile_url(dest.toString());
		
		notifyDownloadService(context);
		return downloadId;
	}

	public long downloadFeed(Context context, Feed feed) {
		return download(context, feeds, feed, new File(
				getFeedfilePath(context), getFeedfileName(feed)));
	}

	public long downloadImage(Context context, FeedImage image) {
		return download(context, images, image, new File(
				getImagefilePath(context), getImagefileName(image)));
	}

	public long downloadMedia(Context context, FeedMedia feedmedia) {
		return download(context, media, feedmedia,
				new File(getMediafilePath(context, feedmedia),
						getMediafilename(feedmedia)));
	}

	/**
	 * Cancels a running download.
	 * 
	 * @param context
	 *            A context needed to get the DownloadManager service
	 * @param id
	 *            ID of the download to cancel
	 * */
	public void cancelDownload(final Context context, final long id) {
		Log.d(TAG, "Cancelling download with id " + id);
		DownloadManager manager = (DownloadManager) context
				.getSystemService(Context.DOWNLOAD_SERVICE);
		int removed = manager.remove(id);
		if (removed > 0) {
			// Delete downloads in lists
			Feed feed = getFeed(id);
			if (feed != null) {
				feeds.remove(feed);
			} else {
				FeedImage image = getFeedImage(id);
				if (image != null) {
					images.remove(image);
				} else {
					FeedMedia m = getFeedMedia(id);
					if (media != null) {
						media.remove(m);
					}
				}
			}
		}
	}

	/** Get a Feed by its download id */
	public Feed getFeed(long id) {
		for (FeedFile f : feeds) {
			if (f.getDownloadId() == id) {
				return (Feed) f;
			}
		}
		return null;
	}

	/** Get a FeedImage by its download id */
	public FeedImage getFeedImage(long id) {
		for (FeedFile f : images) {
			if (f.getDownloadId() == id) {
				return (FeedImage) f;
			}
		}
		return null;
	}

	/** Get media by its download id */
	public FeedMedia getFeedMedia(long id) {
		for (FeedFile f : media) {
			if (f.getDownloadId() == id) {
				return (FeedMedia) f;
			}
		}
		return null;
	}

	public void removeFeed(Feed f) {
		feeds.remove(f);
	}

	public void removeFeedMedia(FeedMedia m) {
		media.remove(m);
	}

	public void removeFeedImage(FeedImage fi) {
		images.remove(fi);
	}

	public ArrayList<FeedFile> getMediaDownloads() {
		return media;
	}

	/** Get the number of uncompleted Downloads */
	public int getNumberOfDownloads() {
		return feeds.size() + images.size() + media.size();
	}

	public int getNumberOfFeedDownloads() {
		return feeds.size();
	}

	public String getFeedfilePath(Context context) {
		return context.getExternalFilesDir(FEED_DOWNLOADPATH).toString() + "/";
	}

	public String getFeedfileName(Feed feed) {
		return "feed-" + NumberGenerator.generateLong(feed.getDownload_url());
	}

	public String getImagefilePath(Context context) {
		return context.getExternalFilesDir(IMAGE_DOWNLOADPATH).toString() + "/";
	}

	public String getImagefileName(FeedImage image) {
		return "image-" + NumberGenerator.generateLong(image.getDownload_url());
	}

	public String getMediafilePath(Context context, FeedMedia media) {
		return context
				.getExternalFilesDir(
						MEDIA_DOWNLOADPATH
								+ media.getItem().getFeed().getTitle() + "/")
				.toString();
	}

	public String getMediafilename(FeedMedia media) {
		return URLUtil.guessFileName(media.getDownload_url(), null,
				media.getMime_type());
	}

	public boolean isDownloaded(Feed feed) {
		return feed.getFile_url() != null && !feeds.contains(feed);
	}

	public boolean isDownloaded(FeedImage image) {
		return image.getFile_url() != null && !images.contains(image);
	}

	public boolean isDownloaded(FeedMedia m) {
		return m.getFile_url() != null && media.contains(m);
	}

	public boolean isDownloading(Feed feed) {
		return feed.getFile_url() != null && feeds.contains(feed);
	}

	public boolean isDownloading(FeedImage image) {
		return image.getFile_url() != null && images.contains(image);
	}

	public boolean isDownloading(FeedMedia m) {
		return m.getFile_url() != null && media.contains(m);
	}

	/*
	 * ------------ Methods for communicating with the DownloadService
	 * -------------
	 */
	private DownloadService mService = null;
	boolean mIsBound;

	private ServiceConnection mConnection = new ServiceConnection() {
		public void onServiceConnected(ComponentName className, IBinder service) {
			mService = ((DownloadService.LocalBinder)service).getService();
			Log.d(TAG, "Connection to service established");
			mService.queryDownloads();
		}

		public void onServiceDisconnected(ComponentName className) {
			mService = null;
			Log.i(TAG, "Closed connection with DownloadService.");
		}
	};

	/** Notifies the DownloadService to check if there are any Downloads left */
	public void notifyDownloadService(Context context) {
		context.bindService(new Intent(context, DownloadService.class),
				mConnection, Context.BIND_AUTO_CREATE);
		mIsBound = true;
		
		context.unbindService(mConnection);
		mIsBound = false;
	}
}
