package com.caisl.loadinghelper;

import android.app.Activity;
import android.support.annotation.NonNull;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import java.util.HashMap;

import static java.util.Objects.requireNonNull;

/**
 * @author caisl
 */
@SuppressWarnings("WeakerAccess")
public final class LoadingHelper {

  public static final int VIEW_TYPE_TITLE = -1;
  public static final int VIEW_TYPE_LOADING = -2;
  public static final int VIEW_TYPE_CONTENT = -3;
  public static final int VIEW_TYPE_ERROR = -4;
  public static final int VIEW_TYPE_EMPTY = -5;

  private static Default sDefault;
  private LinearLayout mDecorView;
  private View mContentView;
  private ViewHolder mCurrentViewHolder;
  private Activity mActivity;
  private OnRetryListener mOnRetryListener;
  private SparseArray<ViewHolder> mViewHolders;
  @NonNull
  private Adapters mAdapters;
  private AdapterDataEvent mAdapterDataEvent;

  public static Default getDefault() {
    if (sDefault == null) {
      synchronized (Default.class) {
        sDefault = new Default();
      }
    }
    return sDefault;
  }

  public LoadingHelper(@NonNull Activity activity) {
    this(activity, null);
  }

  public LoadingHelper(@NonNull Activity activity, ContentAdapter contentAdapter) {
    this(activity, ((ViewGroup) activity.findViewById(android.R.id.content)).getChildAt(0), contentAdapter);
  }

  public LoadingHelper(@NonNull View contentView) {
    this(contentView, null);
  }

  public LoadingHelper(@NonNull View contentView, ContentAdapter contentAdapter) {
    this(null, contentView, contentAdapter);
  }

  private LoadingHelper(Activity activity, @NonNull View contentView, ContentAdapter contentAdapter) {
    mActivity = activity;
    mContentView = requireNonNull(contentView);
    mViewHolders = new SparseArray<>();
    mAdapters = getDefault().mAdapters.clone();
    mAdapterDataEvent = new AdapterDataEvent() {
      @SuppressWarnings("unchecked")
      @Override
      public void notifyDataSetChanged(Adapter adapter) {
        adapter.onBindViewHolder(getViewHolder(mAdapters.getViewType(adapter)));
      }
    };
    if (contentAdapter != null) {
      registerAdapter(VIEW_TYPE_CONTENT, contentAdapter);
    }

    mDecorView = new LinearLayout(contentView.getContext());
    mDecorView.setOrientation(LinearLayout.VERTICAL);
    mDecorView.setLayoutParams(contentView.getLayoutParams());
    ViewGroup parent = (ViewGroup) contentView.getParent();
    if (parent != null) {
      int index = parent.indexOfChild(contentView);
      parent.removeView(contentView);
      parent.addView(mDecorView, index);
    }
    showView(VIEW_TYPE_CONTENT);
  }

  public void registerTitleAdapter(@NonNull Adapter<?> adapter) {
    registerAdapter(VIEW_TYPE_TITLE, adapter);
  }

  public void registerAdapter(int viewType, @NonNull Adapter<?> adapter) {
    mAdapters.register(viewType, adapter);
  }

  public void registerMethod(int viewType, @NonNull Object flag, @NonNull Method method) {
    getAdapter(viewType).registerMethod(flag, method);
  }

  public boolean unregisterAdapter(int viewType) {
    return mAdapters.unregister(viewType);
  }

  public boolean unregisterMethod(int viewType, @NonNull Object flag) {
    return getAdapter(viewType).unregisterMethod(flag);
  }

  public void addTitleView() {
    addHeaderView(VIEW_TYPE_TITLE);
  }

  public void addHeaderView(int viewType) {
    mDecorView.addView(getView(viewType), 0);
  }

  public void showLoadingView() {
    showView(VIEW_TYPE_LOADING);
  }

  public void showContentView() {
    showView(VIEW_TYPE_CONTENT);
  }

  public void showErrorView() {
    showView(VIEW_TYPE_ERROR);
  }

  public void showEmptyView() {
    showView(VIEW_TYPE_EMPTY);
  }

  public void showView(int viewType) {
    if (mCurrentViewHolder == null) {
      addView(viewType);
    } else {
      if (viewType != mCurrentViewHolder.viewType) {
        mDecorView.removeView(mCurrentViewHolder.rootView);
        addView(viewType);
      }
    }
  }

  private void addView(int viewType) {
    ViewHolder viewHolder = getViewHolder(viewType);
    mDecorView.addView(viewHolder.rootView);
    mCurrentViewHolder = viewHolder;
  }

  public void executeMethod(int viewType, Object flag, Object... params) {
    getViewHolder(viewType).executeMethod(flag, params);
  }

  public <T> T getMethodReturnValue(int viewType, Object flag, Object... params) {
    return getViewHolder(viewType).getMethodReturnValue(flag, params);
  }

  public void setOnRetryListener(OnRetryListener onRetryListener) {
    mOnRetryListener = onRetryListener;
  }

  public LinearLayout getDecorView() {
    return mDecorView;
  }

  @SuppressWarnings("unchecked")
  private <T extends Adapter> T getAdapter(int viewType) {
    return (T) mAdapters.getAdapter(viewType);
  }

  @NonNull
  private ViewHolder getViewHolder(int viewType) {
    if (mViewHolders.get(viewType) == null) {
      addViewHolder(viewType);
    }
    return mViewHolders.get(viewType);
  }

  private View getView(int viewType) {
    return getViewHolder(viewType).rootView;
  }

  @SuppressWarnings("unchecked")
  private void addViewHolder(int viewType) {
    final Adapter adapter = mAdapters.getAdapter(viewType);
    ViewHolder viewHolder;
    if (adapter instanceof ContentAdapter) {
      final ContentAdapter contentAdapter = (ContentAdapter) adapter;
      contentAdapter.onCreate(mContentView);
    }
    viewHolder = adapter.onCreateViewHolder(LayoutInflater.from(mDecorView.getContext()), mDecorView);
    viewHolder.viewType = viewType;
    viewHolder.onRetryListener = mOnRetryListener;
    if (viewHolder instanceof ContentViewHolder) {
      ((ContentViewHolder) viewHolder).activity = mActivity;
    }
    mViewHolders.append(viewType, viewHolder);
    adapter.adapterDataEvent = mAdapterDataEvent;
    adapter.onBindViewHolder(viewHolder);
  }

  public static class Default {
    Adapters mAdapters = new Adapters();

    public Default registerAdapter(int viewType, @NonNull Adapter<?> adapter) {
      mAdapters.register(viewType, adapter);
      return this;
    }

    public Default registerMethod(int viewType, @NonNull Object flag, @NonNull Method method) {
      mAdapters.getAdapter(viewType).registerMethod(flag, method);
      return this;
    }
  }

  public static abstract class Adapter<VH extends LoadingHelper.ViewHolder> {
    AdapterDataEvent adapterDataEvent;
    HashMap<Object, Method> mMethods;

    @NonNull
    public abstract VH onCreateViewHolder(@NonNull LayoutInflater inflater, @NonNull ViewGroup parent);

    public abstract void onBindViewHolder(@NonNull VH holder);

    public void registerMethod(@NonNull Object flag, @NonNull Method method) {
      getMethods().put(requireNonNull(flag), requireNonNull(method));
    }

    public boolean unregisterMethod(@NonNull Object flag) {
      return mMethods.remove(requireNonNull(flag)) != null;
    }

    public void notifyDataSetChanged() {
      adapterDataEvent.notifyDataSetChanged(this);
    }

    HashMap<Object, Method> getMethods() {
      if (mMethods == null) {
        mMethods = new HashMap<>();
      }
      return mMethods;
    }
  }

  public static abstract class ContentAdapter<VH extends LoadingHelper.ContentViewHolder> extends Adapter<VH> {
    View mContentView;

    void onCreate(View contentView) {
      mContentView = contentView;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull LayoutInflater inflater, @NonNull ViewGroup parent) {
      return onCreateViewHolder(requireNonNull(inflater), requireNonNull(parent), requireNonNull(mContentView));
    }

    public abstract VH onCreateViewHolder(@NonNull LayoutInflater inflater, @NonNull ViewGroup parent,
                                          @NonNull View contentView);
  }

  public static class Adapters implements Cloneable {
    private SparseArray<Adapter> mAdapters;

    public Adapters() {
      mAdapters = new SparseArray<>();
    }

    public void register(int viewType, @NonNull Adapter<?> adapter) {
      mAdapters.append(viewType, requireNonNull(adapter));
    }

    public boolean unregister(int viewType) {
      mAdapters.remove(viewType);
      return mAdapters.get(viewType) == null;
    }

    @NonNull
    public Adapter<?> getAdapter(int viewType) {
      Adapter adapter = mAdapters.get(viewType);
      if (adapter == null && viewType == VIEW_TYPE_CONTENT) {
        adapter = new LoadingHelper.ContentAdapter() {
          @Override
          public ContentViewHolder onCreateViewHolder(@NonNull LayoutInflater inflater, @NonNull ViewGroup parent,
                                                      @NonNull View contentView) {
            return new ContentViewHolder(contentView);
          }

          @Override
          public void onBindViewHolder(@NonNull LoadingHelper.ViewHolder holder) {
          }
        };
        register(VIEW_TYPE_CONTENT, adapter);
      }
      return requireNonNull(adapter, "Adapter is unregistered");
    }

    public int getViewType(Adapter adapter) {
      int index = mAdapters.indexOfValue(adapter);
      return mAdapters.keyAt(index);
    }

    public Adapters clone() {
      Adapters clone = null;
      try {
        clone = (Adapters) super.clone();
        clone.mAdapters = this.mAdapters.clone();
      } catch (CloneNotSupportedException e) {
        e.printStackTrace();
      }
      return clone;
    }
  }

  public static class ViewHolder {
    @NonNull
    public final View rootView;
    public OnRetryListener onRetryListener;
    int viewType;
    HashMap<Object, Method> methods;

    public ViewHolder(@NonNull View rootView) {
      this.rootView = requireNonNull(rootView);
    }

    @SuppressWarnings("unchecked")
    public void executeMethod(@NonNull Object flag, Object... params) {
      requireNonNull(methods.get(requireNonNull(flag))).execute(this, params);
    }

    @SuppressWarnings("unchecked")
    <T> T getMethodReturnValue(Object flag, Object... params) {
      return (T) requireNonNull(methods.get(requireNonNull(flag))).execute(this, params);
    }
  }

  public static class ContentViewHolder extends ViewHolder {
    public Activity activity;

    public ContentViewHolder(@NonNull View rootView) {
      super(rootView);
    }
  }

  public interface Method<T, VH extends ViewHolder> {
    T execute(VH holder, Object... params);
  }

  public interface OnRetryListener {
    void onRetry();
  }

  interface AdapterDataEvent {
    void notifyDataSetChanged(Adapter adapter);
  }
}