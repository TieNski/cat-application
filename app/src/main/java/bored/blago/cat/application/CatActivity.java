package bored.blago.cat.application;
import android.app.AlarmManager;
import android.app.Dialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;
import android.os.Environment;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageView;
import android.widget.Toast;
import com.android.volley.Request;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.target.Target;
import com.bumptech.glide.request.transition.Transition;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.android.flexbox.AlignItems;
import com.google.android.flexbox.FlexDirection;
import com.google.android.flexbox.FlexboxLayoutManager;
import com.google.android.flexbox.JustifyContent;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import uk.co.senab.photoview.PhotoViewAttacher;

public class CatActivity extends AppCompatActivity {
    private ObjectMapper mCatConverter = new ObjectMapper();
    private List<String> catAdjectives = Arrays.asList("", "Cute ", "Adorable ", "Funny ", "Witty ", "Hysterical ", "Amusing ");
    private String mCatUrl = getCatUrl();
    private int mCatIndex = 0;
    private CatAdapter mCatsAdapter;
    private List<Map<String, Object>> mCatsAdapterItems = new ArrayList<>();
    private CatsModel mCats;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP_MR1) {
            requestPermissions(new String[] {"android.permission.WRITE_EXTERNAL_STORAGE"},1234567890);
        }
        setContentView(R.layout.cat_activity);
        setSupportActionBar(findViewById(R.id.toolbar));
        mCatConverter.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mCatConverter.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);

        FlexboxLayoutManager catsManager = new FlexboxLayoutManager(this);
        catsManager.setFlexDirection(FlexDirection.ROW);
        catsManager.setJustifyContent(JustifyContent.FLEX_END);
        RecyclerView catGridView = findViewById(R.id.recycler);
        catGridView.setLayoutManager(catsManager);
        catGridView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                if (!recyclerView.canScrollVertically(1)) {
                    displayCats();
                }
            }
        });
        mCatsAdapter = new CatAdapter(mCatsAdapterItems);
        catGridView.setAdapter(mCatsAdapter);

        String savedResult = getSharedPreferences("CATS.PREF", Context.MODE_PRIVATE).getString(mCatUrl, "");
        if (TextUtils.isEmpty(savedResult)) {
            requestNewCats();
        } else {
            convertAndDisplayCats(savedResult);
        }
    }

    private void convertAndDisplayCats(String cats) {
        try {
            mCats = mCatConverter.readValue(cats.getBytes(), CatsModel.class);
            displayCats();        //If this happens - the API has changed we want crash- TODO add crashlytics for report
        } catch (IOException e) { throw new RuntimeException(e); }
    }

    private void displayCats() {
        if (mCats != null) {
            int catPageIncrement = 25;
            for (int i = mCatIndex; i < mCatIndex + catPageIncrement && i <mCats.getItems().size(); i++) {
                mCatsAdapterItems.add(mCats.getItems().get(i));
                mCatsAdapter.notifyItemInserted(mCatIndex + i);
            }
            mCatIndex += catPageIncrement;
        }
    }

    private void requestNewCats() {
        mCatUrl = getCatUrl();
        Volley.newRequestQueue(this).add(getRequest());
    }

    private String getCatUrl() {
        return "https://api.qwant.com/api/search/images?q=" + catAdjectives.get(new Random().nextInt(catAdjectives.size())) + "cat&t=images&safesearch=1&locane=en_US&uiv=4&count=250";
    }

    private JsonObjectRequest getRequest() {
        return new JsonObjectRequest(Request.Method.GET, mCatUrl, null,
            response -> {
                try {
                    JSONObject result = response.getJSONObject("data").getJSONObject("result");
                    JSONArray items = result.getJSONArray("items");
                    if (items.length() > 0) {
                        String cats = result.toString();
                        getSharedPreferences("CATS.PREF", Context.MODE_PRIVATE).edit().putString(mCatUrl, cats).commit();
                        convertAndDisplayCats(cats);
                    } else {
                        requestNewCats();
                    }
                } catch (JSONException e) { e.printStackTrace(); }
            }, error -> Toast.makeText(CatActivity.this, R.string.internet_trouble, Toast.LENGTH_LONG).show());
    }
                        //TODO Gifs are not Bitmaps, must save differently
    private void saveCat(final Bitmap bitmap) {
        new Thread(() -> {
            File catsDir = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/Cats");
            if (!catsDir.exists()) {
                catsDir.mkdirs();
            }
            File catImage = new File(catsDir, "cat-" + UUID.randomUUID().toString() + ".png");
            boolean catSaved = false;
            FileOutputStream catSream = null;
            try {
                catImage.createNewFile();
                catSream = new FileOutputStream(catImage);
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, catSream);
                catSream.flush();
                catSaved = true;
            } catch (IOException e) {
                e.printStackTrace();
            } finally {                                                //If we get here, the device is on fire
                if (catSream != null) { try { catSream.close(); } catch (IOException e) { e.printStackTrace(); } }
            }
            final int res = catSaved ? R.string.saved : R.string.not_saved;
            CatActivity.this.runOnUiThread(() -> {
                Toast.makeText(CatActivity.this, res, Toast.LENGTH_LONG).show();
                CatActivity.this.sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE).setData(Uri.fromFile(catImage)));
            });
        }).start();
    }

    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_bored, menu);
        return true;
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_about) {
            final Dialog d = new Dialog(this);
            d.setContentView(getLayoutInflater().inflate(R.layout.about_bored_blago, null));
            d.setCancelable(true);
            d.getWindow().getDecorView().addOnLayoutChangeListener((view, i, i1, i2, i3, i4, i5, i6, i7) -> {
                Rect rect = new Rect();
                Window window = d.getWindow();
                view.getWindowVisibleDisplayFrame(rect);
                int maxHeight = (int) (rect.height()*0.5f);
                if (view.getHeight() != maxHeight) {
                    window.setLayout(window.getAttributes().width, maxHeight);
                }
            });
            d.show();
            return true;
        } else if (item.getItemId() == R.id.action_random) {
            ((AlarmManager) this.getSystemService(Context.ALARM_SERVICE)).set(AlarmManager.RTC, System.currentTimeMillis() + 150, PendingIntent.getActivity(this, 1, new Intent(this, CatActivity.class), PendingIntent.FLAG_CANCEL_CURRENT));
            System.exit(0);
        }
        return super.onOptionsItemSelected(item);
    }

    static class CatsModel {
        private List<Map<String, Object>> items;
        public List<Map<String, Object>> getItems() {
            return items;
        }
        public void setItems(List<Map<String, Object>> items) {
            this.items = items;
        }
    }

    class CatAdapter extends RecyclerView.Adapter {
        private List<Map<String, Object>> cats;
        CatAdapter(List<Map<String, Object>> cats) {
            this.cats = cats;
        }
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new CatHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.cat_adapter_item, parent, false));
        }
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            ((CatHolder) holder).bind(cats.get(position));
        }
        public int getItemCount() {
            return cats.size();
        }
    }

    class CatHolder extends RecyclerView.ViewHolder {
        private ImageView catView;

        CatHolder(View itemView) {
            super(itemView);
            catView = itemView.findViewById(R.id.cat_view);
            ViewGroup.LayoutParams lp = catView.getLayoutParams();
            if (lp instanceof FlexboxLayoutManager.LayoutParams) {
                FlexboxLayoutManager.LayoutParams flexboxLp = (FlexboxLayoutManager.LayoutParams) lp;
                flexboxLp.setFlexGrow(1.0f);
                flexboxLp.setAlignSelf(AlignItems.FLEX_END);
            }
        }

        void bind(Map<String, Object> entry) {
            final String showMedia = ((String) entry.get("media")).replaceAll("\\\\", "/");
            Glide.with(CatActivity.this).load(showMedia).error(R.drawable.error).dontTransform().override(350,350).into(catView);

            catView.setOnClickListener(view -> {
                View aboutBored = LayoutInflater.from(CatActivity.this).inflate(R.layout.cat_zoom_popup, null);
                ImageView zoomImageView = aboutBored.findViewById(R.id.cat_zoom_view);
                Glide.with(CatActivity.this).load(showMedia).centerInside().into(zoomImageView);
                new PhotoViewAttacher(zoomImageView);

                aboutBored.findViewById(R.id.save_cat).setOnClickListener(view1 -> Glide.with(CatActivity.this).asBitmap().load(showMedia)
                    .into(new CustomTarget<Bitmap>(Target.SIZE_ORIGINAL,Target.SIZE_ORIGINAL) {
                        public void onResourceReady(Bitmap bitmap, Transition<? super Bitmap> transition) {
                            saveCat(bitmap);
                        }
                        public void onLoadCleared(Drawable placeholder) {}
                    }));
                final Dialog d = new Dialog(CatActivity.this);
                d.setContentView(aboutBored);
                d.setCancelable(true);
                d.show();
            });
        }
    }
}