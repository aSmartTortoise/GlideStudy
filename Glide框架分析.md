Glide框架分析

参考文章：

https://muyangmin.github.io/glide-docs-cn/

[Glide v4源码解析](https://blog.yorek.xyz/android/3rd-library/glide1/)

https://github.com/aSmartTortoise/GlideStudy

# 1 关于Glide

Glide是一个快速高效的Android图片加载库。支持拉取、解码、缩放、展示视频快照、图片和GIF。该分析基于glide:4.9.0版本。

# 2 简单使用

多数情况下，使用Glide加载图片非常简单。

```k
Glide.with(this).load(URL).into(ivGlide!!)
```

Glide.with方法有很多重载：

. with(@NonNull Context context)

. with(@NonNull View view)

. with(@NonNull Activity activity)

. with(@NonNull FragmentActivity activity)

. with(@NonNull Fragment fragment)

在上面的重载方法中，除了前两个重载方法外，其他三个都有直观的生命周期；至于前两个，会尝试绑定到Activity或Fragment上，如果失败了就绑定到Application上。

Glide.with返回一个RequestManager实例，其load方法也有很多重载：

. load(@Nullable Bitmap bitmap)

. load(@Nullable Drawable drawable)

. load(@Nullable String string)

. load(@Nullable Uri uri)

. load(@Nullable File file)

. load(@RawRes @DrawableRes @Nullable Integer resourceId)

. load(@Nullable URL url)

. load(@Nullable byte[] model)

. load(@Nullable Object model)

RequestManager.load之后返回一个RequestBuild对象，调用该对象的into(@NonNull ImageView view)就可以完成加载图片到一个ImageView上展示。

## 2.1 占位符

占位符有三种，分别在三种不同场景使用：

. placeholder

placeholder是请求中展示的Drawable。当请求完成时，placeholder会被请求到的资源替换。如果请求的资源是从内存中加载出来的，那么placeholder可能根本不会被显示。如果请求的url/model为null，且没有设置error或fallback，则placeholder继续展示。

. error

error在请求永久性失败时展示。error同样在请求的url/model为null，且没有设置fallback时展示。

. fallback

fallback在请求的url/model为null时展示。设计fallback的主要目的是允许用户指示null是否为可接受的。例如一个url为null可能暗示这个用户没有设置头像，因此因该使用默认头像。然而，null也可能表明这个元数据根本就是不合法的，获取取不到。默认情况下Glide将null作错误处理。

![Glide占位符显示逻辑](C:\Users\wangjie\Desktop\study\Glide\imgs\glide-placeholders-show-logic.png)

在加载图片的时候，占位符可以通过RequestOptions设置，通过RequestOptions可以设置加载的资源是否启用内存缓存或磁盘缓存。

比如

```k
        val options = RequestOptions()
            .placeholder(ColorDrawable(Color.GRAY))
            .error(ColorDrawable(Color.RED))
            .fallback(ColorDrawable(Color.CYAN))
            .skipMemoryCache(true)
            .diskCacheStrategy(DiskCacheStrategy.NONE)
        Glide.with(this).load(URL).apply(options).into(ivGlide!!)
```

通过RequestOptions设置占位符，设置不使用内存缓存和磁盘缓存。

占位符不是异步加载的。

## 2.2 指定图片格式

Glide支持加载GIF。

例如

```k
        val options = RequestOptions()
            .placeholder(ColorDrawable(Color.GRAY))
            .error(ColorDrawable(Color.RED))
            .fallback(ColorDrawable(Color.CYAN))
            .skipMemoryCache(true)
            .diskCacheStrategy(DiskCacheStrategy.NONE)
        Glide.with(this).load(GIF_URL).apply(options).into(ivGlide!!)
```

指定GIF的uri使用常用的加载图片的调用即可加载GIF到ImageView上。

### 2.2.1 GIF资源展示静态图片

如果加载的图片是GIF，但希望展示的效果是个静态图片，可以在Glide.with之后得到的RequestManager对象调用asBitmap方法即可。

这样展示的是第一帧图片。

```k
        val options = RequestOptions()
            .placeholder(ColorDrawable(Color.GRAY))
            .error(ColorDrawable(Color.RED))
            .fallback(ColorDrawable(Color.CYAN))
            .skipMemoryCache(true)
            .diskCacheStrategy(DiskCacheStrategy.NONE)
        Glide.with(this).asBitmap().load(GIF_URL).apply(options).into(ivGlide!!)
```

## 2.3 指定图片大小

我们平时在加载图片的时候很容易出现内存浪费。比如一张图片的尺寸是1000*1000像素，但是界面上的ImageView只有200 * 200像素，这个时候如果不对图片进行压缩直接读取到内存中，就属于内存浪费了。

使用Glide，不用担心内存浪费的问题。Glide会自行判断ImageView的尺寸，然后将对应像素大小的图片加载到内存中。在绝大多数情况下使用Glide不需要指定图片的大小。

通过RequestBuilder.override指定加载图片的大小。

```k
        val options = RequestOptions()
            .placeholder(ColorDrawable(Color.GRAY))
            .error(ColorDrawable(Color.RED))
            .fallback(ColorDrawable(Color.CYAN))
            .skipMemoryCache(true)
            .diskCacheStrategy(DiskCacheStrategy.NONE)
        Glide.with(this)
            .load(URL)
            .apply(options)
            .override(100, 100)
            .into(ivGlide!!)
```

# 3 从源码的角度理解Glide加载图片的三步执行流程

## 3.1 Glide#with

Glide.with每个重载的方法内部都首先调用getRetriver(@Nullable Context context)方法获取一个RequestManagerRetriver对象，然后再调用get方法返回RequestManager。传入getRetriver的参数都是Context，而RequestManagerRetriver#get方法传入的参数各不相同，所以生命周期的绑定肯定发生在get方法中。

### 3.1.1 Glide#getRetriver(@Nullable Context context)

getRetriver(Context)方法会根据@GlideModule注解的类以及AndroidMenifest文件中meta-data配置的GlideModule来创建一个Glide的实例，然后返回该实例的RequestManagerRetriver。

```java
  @NonNull
  private static RequestManagerRetriever getRetriever(@Nullable Context context) {
    // Context could be null for other reasons (ie the user passes in null), but in practice it will
    // only occur due to errors with the Fragment lifecycle.
    Preconditions.checkNotNull(
        context,
        "You cannot start a load on a not yet attached View or a Fragment where getActivity() "
            + "returns null (which usually occurs when getActivity() is called before the Fragment "
            + "is attached or after the Fragment is destroyed).");
    return Glide.get(context).getRequestManagerRetriever();
  }
```

因入参context为fragment.getActivity()时，可能为null，所以这里进行了一次判空。然后调用了Glide.get(context)创建一个Glide单例，最后将RequestManagerRetriver返回。

```java
  @NonNull
  public static Glide get(@NonNull Context context) {
    if (glide == null) {
      synchronized (Glide.class) {
        if (glide == null) {
          checkAndInitializeGlide(context);
        }
      }
    }

    return glide;
  }

  private static void checkAndInitializeGlide(@NonNull Context context) {
    // In the thread running initGlide(), one or more classes may call Glide.get(context).
    // Without this check, those calls could trigger infinite recursion.
    if (isInitializing) {
      throw new IllegalStateException("You cannot call Glide.get() in registerComponents(),"
          + " use the provided Glide instance instead");
    }
    isInitializing = true;
    initializeGlide(context);
    isInitializing = false;
  }

  private static void initializeGlide(@NonNull Context context) {
    initializeGlide(context, new GlideBuilder());
  }

  @SuppressWarnings("deprecation")
  private static void initializeGlide(@NonNull Context context, @NonNull GlideBuilder builder) {
    Context applicationContext = context.getApplicationContext();
    // 如果有配置@GlideModule注解，那么会反射构造kapt生成的GeneratedAppGlideModuleImpl类
    GeneratedAppGlideModule annotationGeneratedModule = getAnnotationGeneratedGlideModules();
    List<com.bumptech.glide.module.GlideModule> manifestModules = Collections.emptyList();
    // 如果Impl存在，且允许解析manifest文件
  	// 则遍历manifest中的meta-data，解析出所有的GlideModule类
    if (annotationGeneratedModule == null || annotationGeneratedModule.isManifestParsingEnabled()) {
      manifestModules = new ManifestParser(applicationContext).parse();
    }
	// 根据Impl的黑名单，剔除manifest中的GlideModule类
    if (annotationGeneratedModule != null
        && !annotationGeneratedModule.getExcludedModuleClasses().isEmpty()) {
      Set<Class<?>> excludedModuleClasses =
          annotationGeneratedModule.getExcludedModuleClasses();
      Iterator<com.bumptech.glide.module.GlideModule> iterator = manifestModules.iterator();
      while (iterator.hasNext()) {
        com.bumptech.glide.module.GlideModule current = iterator.next();
        if (!excludedModuleClasses.contains(current.getClass())) {
          continue;
        }
        if (Log.isLoggable(TAG, Log.DEBUG)) {
          Log.d(TAG, "AppGlideModule excludes manifest GlideModule: " + current);
        }
        iterator.remove();
      }
    }

    if (Log.isLoggable(TAG, Log.DEBUG)) {
      for (com.bumptech.glide.module.GlideModule glideModule : manifestModules) {
        Log.d(TAG, "Discovered GlideModule from manifest: " + glideModule.getClass());
      }
    }
	// 如果Impl存在，那么设置为该类的RequestManagerFactory； 否则，设置为null
    RequestManagerRetriever.RequestManagerFactory factory =
        annotationGeneratedModule != null
            ? annotationGeneratedModule.getRequestManagerFactory() : null;
    builder.setRequestManagerFactory(factory);
    // 依次调用manifest中GlideModule类的applyOptions方法，将配置写到builder里
    for (com.bumptech.glide.module.GlideModule module : manifestModules) {
      module.applyOptions(applicationContext, builder);
    }
    // 写入Impl的配置
  	// 也就是说Impl配置的优先级更高，如果有冲突的话
    if (annotationGeneratedModule != null) {
      annotationGeneratedModule.applyOptions(applicationContext, builder);
    }
    // 🔥🔥🔥调用GlideBuilder.build方法创建Glide
    Glide glide = builder.build(applicationContext);
    // 依次调用manifest中GlideModule类的registerComponents方法，来替换Glide的默认配置
    for (com.bumptech.glide.module.GlideModule module : manifestModules) {
      module.registerComponents(applicationContext, glide, glide.registry);
    }
    // 调用Impl中替换Glide配置的方法
    if (annotationGeneratedModule != null) {
      annotationGeneratedModule.registerComponents(applicationContext, glide, glide.registry);
    }
    // 注册内存管理的回调，因为Glide实现了ComponentCallbacks2接口
    applicationContext.registerComponentCallbacks(glide);
    Glide.glide = glide;
  }
```

在我们本节的例子中，我们`AndroidManifest`和`@GlideModule`注解中都没有进行过配置，所以上面的代码可以简化为：

```java
@SuppressWarnings("deprecation")
private static void initializeGlide(@NonNull Context context, @NonNull GlideBuilder builder) {
  Context applicationContext = context.getApplicationContext();
  // 🔥🔥🔥调用GlideBuilder.build方法创建Glide
  Glide glide = builder.build(applicationContext);
  // 注册内存管理的回调，因为Glide实现了ComponentCallbacks2接口
  applicationContext.registerComponentCallbacks(glide);
  // 保存glide实例到静态变量中
  Glide.glide = glide;
}
```

🔥🔥🔥我们看一下`GlideBuilder.build`方法：

```java
@NonNull
Glide build(@NonNull Context context) {
  ...

  RequestManagerRetriever requestManagerRetriever =
      new RequestManagerRetriever(requestManagerFactory);

  return new Glide(
      context,
      engine,
      memoryCache,
      bitmapPool,
      arrayPool,
      requestManagerRetriever,
      connectivityMonitorFactory,
      logLevel,
      defaultRequestOptions.lock(),
      defaultTransitionOptions,
      defaultRequestListeners,
      isLoggingRequestOriginsEnabled);
}
```

这里的requestManagerRetriver直接调用了构造器，且传入实际参数为null，在RequestManagerRetriver的构造方法中会使用默认的DEFAULT_FACTORY:

```JAVA
public class RequestManagerRetriever implements Handler.Callback {   
   /**
   * Main thread handler to handle cleaning up pending fragment maps.
   */
  private final Handler handler;
  private final RequestManagerFactory factory;

  public RequestManagerRetriever(@Nullable RequestManagerFactory factory) {
    this.factory = factory != null ? factory : DEFAULT_FACTORY;
    handler = new Handler(Looper.getMainLooper(), this /* Callback */);
  }

  /**
   * Used internally to create {@link RequestManager}s.
   */
  public interface RequestManagerFactory {
    @NonNull
    RequestManager build(
        @NonNull Glide glide,
        @NonNull Lifecycle lifecycle,
        @NonNull RequestManagerTreeNode requestManagerTreeNode,
        @NonNull Context context);
  }

  private static final RequestManagerFactory DEFAULT_FACTORY = new RequestManagerFactory() {
    @NonNull
    @Override
    public RequestManager build(@NonNull Glide glide, @NonNull Lifecycle lifecycle,
        @NonNull RequestManagerTreeNode requestManagerTreeNode, @NonNull Context context) {
      return new RequestManager(glide, lifecycle, requestManagerTreeNode, context);
    }
  };
}
```

Glide单例就这样被创建出来了，其RequestManagerRetriver会作为getRetriver(Context)的返回值返回。

接下来回到Glide#with方法中，执行RequestMangerRetriver#get方法，该方法根据入参时生命周期可感的。

### 3.1.2 RequestManagerRetriver#get

该方法也有几个重载的方法

```java
  @NonNull
  private RequestManager getApplicationManager(@NonNull Context context) {
    // Either an application context or we're on a background thread.
    if (applicationManager == null) {
      synchronized (this) {
        if (applicationManager == null) {
          // Normally pause/resume is taken care of by the fragment we add to the fragment or
          // activity. However, in this case since the manager attached to the application will not
          // receive lifecycle events, we must force the manager to start resumed using
          // ApplicationLifecycle.

          // TODO(b/27524013): Factor out this Glide.get() call.
          Glide glide = Glide.get(context.getApplicationContext());
          applicationManager =
              factory.build(
                  glide,
                  new ApplicationLifecycle(),
                  new EmptyRequestManagerTreeNode(),
                  context.getApplicationContext());
        }
      }
    }

    return applicationManager;
  }

  @NonNull
  public RequestManager get(@NonNull Context context) {
    if (context == null) {
      throw new IllegalArgumentException("You cannot start a load on a null Context");
    } else if (Util.isOnMainThread() && !(context instanceof Application)) {
      if (context instanceof FragmentActivity) {
        return get((FragmentActivity) context);
      } else if (context instanceof Activity) {
        return get((Activity) context);
      } else if (context instanceof ContextWrapper) {
        return get(((ContextWrapper) context).getBaseContext());
      }
    }

    return getApplicationManager(context);
  }

  @NonNull
  public RequestManager get(@NonNull FragmentActivity activity) {
    if (Util.isOnBackgroundThread()) {
      return get(activity.getApplicationContext());
    } else {
      assertNotDestroyed(activity);
      FragmentManager fm = activity.getSupportFragmentManager();
      return supportFragmentGet(
          activity, fm, /*parentHint=*/ null, isActivityVisible(activity));
    }
  }

  @NonNull
  public RequestManager get(@NonNull Fragment fragment) {
    Preconditions.checkNotNull(fragment.getActivity(),
          "You cannot start a load on a fragment before it is attached or after it is destroyed");
    if (Util.isOnBackgroundThread()) {
      return get(fragment.getActivity().getApplicationContext());
    } else {
      FragmentManager fm = fragment.getChildFragmentManager();
      return supportFragmentGet(fragment.getActivity(), fm, fragment, fragment.isVisible());
    }
  }

  @SuppressWarnings("deprecation")
  @NonNull
  public RequestManager get(@NonNull Activity activity) {
    if (Util.isOnBackgroundThread()) {
      return get(activity.getApplicationContext());
    } else {
      assertNotDestroyed(activity);
      android.app.FragmentManager fm = activity.getFragmentManager();
      return fragmentGet(
          activity, fm, /*parentHint=*/ null, isActivityVisible(activity));
    }
  }

  @SuppressWarnings("deprecation")
  @NonNull
  public RequestManager get(@NonNull View view) {
    if (Util.isOnBackgroundThread()) {
      return get(view.getContext().getApplicationContext());
    }

    Preconditions.checkNotNull(view);
    Preconditions.checkNotNull(view.getContext(),
        "Unable to obtain a request manager for a view without a Context");
    Activity activity = findActivity(view.getContext());
    // The view might be somewhere else, like a service.
    if (activity == null) {
      return get(view.getContext().getApplicationContext());
    }

    // Support Fragments.
    // Although the user might have non-support Fragments attached to FragmentActivity, searching
    // for non-support Fragments is so expensive pre O and that should be rare enough that we
    // prefer to just fall back to the Activity directly.
    if (activity instanceof FragmentActivity) {
      Fragment fragment = findSupportFragment(view, (FragmentActivity) activity);
      return fragment != null ? get(fragment) : get(activity);
    }

    // Standard Fragments.
    android.app.Fragment fragment = findFragment(view, activity);
    if (fragment == null) {
      return get(activity);
    }
    return get(fragment);
  }
```

在这些get方法中，首先判断当前线程是不是后台线程，如果时后台线程那么就会调用getApplicationManager方法返回一个RequestManager。

如果当前线程不是后台线程，get(View)和get(Context)会根据情况调用get(Fragment)或get(FragmentActivity)。其中get(View)为了找到一个合适的Fragment或fallbackActivity，内部操作比较大，开销比较大，不要轻易使用。

get(Fragment)和get(FragmentActivity)方法都会调用supportFragmentGet方法。

Glide会使用加载目标关联的宿主Activity或宿主Fragment的一个子Fragment来保存一个RequestManager。而RequestManger被Glide用来开始、停止、管理Glide请求。



```java
  @NonNull
  private RequestManager supportFragmentGet(
      @NonNull Context context,
      @NonNull FragmentManager fm,
      @Nullable Fragment parentHint,
      boolean isParentVisible) {
    // 🐟🐟🐟获取一个SupportRequestManagerFragment
    SupportRequestManagerFragment current =
        getSupportRequestManagerFragment(fm, parentHint, isParentVisible);
    // 获取里面的RequestManager对象
    RequestManager requestManager = current.getRequestManager();
    if (requestManager == null) {
      // TODO(b/27524013): Factor out this Glide.get() call.
      Glide glide = Glide.get(context);
      requestManager =
          factory.build(
              glide, current.getGlideLifecycle(), current.getRequestManagerTreeNode(), context);
      // 设置到SupportRequestManagerFragment里面，下次就不需要创建了
      current.setRequestManager(requestManager);
    }
    return requestManager;
  }

  @NonNull
  private SupportRequestManagerFragment getSupportRequestManagerFragment(
      @NonNull final FragmentManager fm, @Nullable Fragment parentHint, boolean isParentVisible) {
    // 已经添加过了，可以直接返回
    SupportRequestManagerFragment current =
        (SupportRequestManagerFragment) fm.findFragmentByTag(FRAGMENT_TAG);
    if (current == null) {
      // 从map中获取，取到也可以返回了
      current = pendingSupportRequestManagerFragments.get(fm);
      if (current == null) {
        // 都没有，那么就创建一个，此时lifecycle默认为ActivityFragmentLifecycle
        current = new SupportRequestManagerFragment();
        // 对于fragment来说，此方法会以Activity为host创建另外一个SupportRequestManagerFragment
      	// 作为rootRequestManagerFragment
      	// 并会将current加入到rootRequestManagerFragment的childRequestManagerFragments中
      	// 在RequestManager递归管理请求时会使用到
        current.setParentFragmentHint(parentHint);
        if (isParentVisible) {
          // 如果当前页面是可见的，那么调用其lifecycle的onStart方法
          current.getGlideLifecycle().onStart();
        }
        pendingSupportRequestManagerFragments.put(fm, current);
        // 将SupportRequestManagerFragment添加到framentManger管理的内存中。
        fm.beginTransaction().add(current, FRAGMENT_TAG).commitAllowingStateLoss();
        handler.obtainMessage(ID_REMOVE_SUPPORT_FRAGMENT_MANAGER, fm).sendToTarget();
      }
    }
    return current;
  }
```

在上一步中Glide单例完成了初始化，这一步成功的创建并返回了一个RequestManger。Glide#with分析完毕。

## 3.2 RequestManger#load

RequestManager#load也有好几个重载的方法。

```java
  @NonNull
  @CheckResult
  @Override
  public RequestBuilder<Drawable> load(@Nullable Bitmap bitmap) {
    return asDrawable().load(bitmap);
  }

  @NonNull
  @CheckResult
  @Override
  public RequestBuilder<Drawable> load(@Nullable Drawable drawable) {
    return asDrawable().load(drawable);
  }

  @NonNull
  @CheckResult
  @Override
  public RequestBuilder<Drawable> load(@Nullable String string) {
    return asDrawable().load(string);
  }

  @NonNull
  @CheckResult
  @Override
  public RequestBuilder<Drawable> load(@Nullable Uri uri) {
    return asDrawable().load(uri);
  }

  @NonNull
  @CheckResult
  @Override
  public RequestBuilder<Drawable> load(@Nullable File file) {
    return asDrawable().load(file);
  }

  @NonNull
  @CheckResult
  @Override
  public RequestBuilder<Drawable> load(@RawRes @DrawableRes @Nullable Integer resourceId) {
    return asDrawable().load(resourceId);
  }

  @SuppressWarnings("deprecation")
  @CheckResult
  @Override
  @Deprecated
  public RequestBuilder<Drawable> load(@Nullable URL url) {
    return asDrawable().load(url);
  }

  @NonNull
  @CheckResult
  @Override
  public RequestBuilder<Drawable> load(@Nullable byte[] model) {
    return asDrawable().load(model);
  }

  @NonNull
  @CheckResult
  @Override
  public RequestBuilder<Drawable> load(@Nullable Object model) {
    return asDrawable().load(model);
  }
```

在所有的RequestManager#load方法内部先调用asDrawable()方法返回一个RequestBuilder对象，然后调用RequestBuilder#load方法。

### 3.2.1 RequestManager#asXxx

asDrawable方法同其他as方法（asBitmap、asGif、asFile）一样，都先调用RequestManager#as方法生成一个RequestBuilder<ResourceType>对象，然后各个as方法会附加一些不同的option。

```java
  @NonNull
  @CheckResult
  public RequestBuilder<Bitmap> asBitmap() {
    return as(Bitmap.class).apply(DECODE_TYPE_BITMAP);
  }

  @NonNull
  @CheckResult
  public RequestBuilder<GifDrawable> asGif() {
    return as(GifDrawable.class).apply(DECODE_TYPE_GIF);
  }

  @NonNull
  @CheckResult
  public RequestBuilder<Drawable> asDrawable() {
    return as(Drawable.class);
  }

  @NonNull
  @CheckResult
  public RequestBuilder<File> asFile() {
    return as(File.class).apply(skipMemoryCacheOf(true));
  }

  @NonNull
  @CheckResult
  public <ResourceType> RequestBuilder<ResourceType> as(
      @NonNull Class<ResourceType> resourceClass) {
    return new RequestBuilder<>(glide, this, resourceClass, context);
  }
```

在RequestBuilder的构造方法中将Drawable.class这样的入参保存到transcodeClass变量中：

```java
  @SuppressLint("CheckResult")
  @SuppressWarnings("PMD.ConstructorCallsOverridableMethod")
  protected RequestBuilder(
      @NonNull Glide glide,
      RequestManager requestManager,
      Class<TranscodeType> transcodeClass,
      Context context) {
    this.glide = glide;
    this.requestManager = requestManager;
    this.transcodeClass = transcodeClass;
    this.context = context;
    this.transitionOptions = requestManager.getDefaultTransitionOptions(transcodeClass);
    this.glideContext = glide.getGlideContext();

    initRequestListeners(requestManager.getDefaultRequestListeners());
    apply(requestManager.getDefaultRequestOptions());
  }
```

然后回到之前的asGif方法中，看看apply(DECODE_TYPE_GIF)干了什么：

```java
RequestManager.java
  private static final RequestOptions DECODE_TYPE_GIF = decodeTypeOf(GifDrawable.class).lock();

  @NonNull
  @CheckResult
  public RequestBuilder<GifDrawable> asGif() {
    return as(GifDrawable.class).apply(DECODE_TYPE_GIF);
  }
```

```java
Requestoptions.java
  @NonNull
  @CheckResult
  public static RequestOptions decodeTypeOf(@NonNull Class<?> resourceClass) {
    return new RequestOptions().decode(resourceClass);
  }
```

```java
BaseRequestOptions.java
  @NonNull
  @CheckResult
  public T decode(@NonNull Class<?> resourceClass) {
    if (isAutoCloneEnabled) {
      return clone().decode(resourceClass);
    }

    this.resourceClass = Preconditions.checkNotNull(resourceClass);
    fields |= RESOURCE_CLASS;
    return selfOrThrowIfLocked();
  }

 @NonNull
  @CheckResult
  public T apply(@NonNull BaseRequestOptions<?> o) {
    if (isAutoCloneEnabled) {
      return clone().apply(o);
    }
    BaseRequestOptions<?> other = o;
	...
    if (isSet(other.fields, RESOURCE_CLASS)) {
      resourceClass = other.resourceClass;
    }
    ...
    return selfOrThrowIfLocked();
  }
```

```java
RequestBuilder.java
  @NonNull
  @CheckResult
  @Override
  public RequestBuilder<TranscodeType> apply(@NonNull BaseRequestOptions<?> requestOptions) {
    Preconditions.checkNotNull(requestOptions);
    return super.apply(requestOptions);
  }
```

不难发现，apply(DECODE_TYPE_GIF)就是将BaseRequestOptions#resourceClass设置为GifDrawable.class；对于asBitmap来说，BaseRequetOptions#resourceClass为Bitmap.class；而对于asDrawable()和asFile()来说，resourceClass没有进行过设置，所以为默认值Object.class。

现在RequestBuilder已经由RequestManager#asDrawable方法生成，接着会调用RequestBuilder#load方法。

### 3.2.2 RequestBuilder#load

RequestBuilder#load方法基本上都会转发给loadGeneric方法，只有少数的方法才会apply额外的options。

```java
RequestBuilder.java
@NonNull
  @CheckResult
  @SuppressWarnings("unchecked")
  @Override
  public RequestBuilder<TranscodeType> load(@Nullable Object model) {
    return loadGeneric(model);
  }

  @NonNull
  private RequestBuilder<TranscodeType> loadGeneric(@Nullable Object model) {
    this.model = model;
    isModelSet = true;
    return this;
  }

  @NonNull
  @CheckResult
  @Override
  public RequestBuilder<TranscodeType> load(@Nullable Bitmap bitmap) {
    return loadGeneric(bitmap)
        .apply(diskCacheStrategyOf(DiskCacheStrategy.NONE));
  }

  @NonNull
  @CheckResult
  @Override
  public RequestBuilder<TranscodeType> load(@Nullable Drawable drawable) {
    return loadGeneric(drawable)
        .apply(diskCacheStrategyOf(DiskCacheStrategy.NONE));
  }

  @NonNull
  @Override
  @CheckResult
  public RequestBuilder<TranscodeType> load(@Nullable String string) {
    return loadGeneric(string);
  }

  @NonNull
  @CheckResult
  @Override
  public RequestBuilder<TranscodeType> load(@Nullable Uri uri) {
    return loadGeneric(uri);
  }

  @NonNull
  @CheckResult
  @Override
  public RequestBuilder<TranscodeType> load(@Nullable File file) {
    return loadGeneric(file);
  }

  @NonNull
  @CheckResult
  @Override
  public RequestBuilder<TranscodeType> load(@RawRes @DrawableRes @Nullable Integer resourceId) {
    return loadGeneric(resourceId).apply(signatureOf(ApplicationVersionSignature.obtain(context)));
  }

  @Deprecated
  @CheckResult
  @Override
  public RequestBuilder<TranscodeType> load(@Nullable URL url) {
    return loadGeneric(url);
  }

  @NonNull
  @CheckResult
  @Override
  public RequestBuilder<TranscodeType> load(@Nullable byte[] model) {
    RequestBuilder<TranscodeType> result = loadGeneric(model);
    if (!result.isDiskCacheStrategySet()) {
        result = result.apply(diskCacheStrategyOf(DiskCacheStrategy.NONE));
    }
    if (!result.isSkipMemoryCacheSet()) {
      result = result.apply(skipMemoryCacheOf(true /*skipMemoryCache*/));
    }
    return result;
  }
```

loadGeneric方法只是将参数保存在model中，并设置isModelSet=true。看来Glide进行图片加载的最核心的步骤应该是RequestBuilder#into方法了。

## 3.3 RequestBuilder#into

RequestBuilder#into有四个重载的方法，最终都调用参数最多的一个：

```java

  @NonNull
  public <Y extends Target<TranscodeType>> Y into(@NonNull Y target) {
    return into(target, /*targetListener=*/ null, Executors.mainThreadExecutor());
  }

  @NonNull
  @Synthetic
  <Y extends Target<TranscodeType>> Y into(
      @NonNull Y target,
      @Nullable RequestListener<TranscodeType> targetListener,
      Executor callbackExecutor) {
    return into(target, targetListener, /*options=*/ this, callbackExecutor);
  }

  private <Y extends Target<TranscodeType>> Y into(
      @NonNull Y target,
      @Nullable RequestListener<TranscodeType> targetListener,
      BaseRequestOptions<?> options,
      Executor callbackExecutor) {
    ...见后文解析
  }

// 最常用的一个重载方法
  @NonNull
  public ViewTarget<ImageView, TranscodeType> into(@NonNull ImageView view) {
    Util.assertMainThread();
    Preconditions.checkNotNull(view);

    BaseRequestOptions<?> requestOptions = this;
    // 若没有指定transformation，isTransformationSet()为false
    // isTransformationAllowed()一般为true，除非主动调用了dontTransform()方法
    if (!requestOptions.isTransformationSet()
        && requestOptions.isTransformationAllowed()
        && view.getScaleType() != null) {
      // Clone in this method so that if we use this RequestBuilder to load into a View and then
      // into a different target, we don't retain the transformation applied based on the previous
      // View's scale type.
      // 根据ImageView的ScaleType设置不同的down sample和transform选项
      switch (view.getScaleType()) {
        case CENTER_CROP:
          requestOptions = requestOptions.clone().optionalCenterCrop();
          break;
        case CENTER_INSIDE:
          requestOptions = requestOptions.clone().optionalCenterInside();
          break;
        case FIT_CENTER:// ImageView 默认的ScaleType
        case FIT_START:
        case FIT_END:
          requestOptions = requestOptions.clone().optionalFitCenter();
          break;
        case FIT_XY:
          requestOptions = requestOptions.clone().optionalCenterInside();
          break;
        case CENTER:
        case MATRIX:
        default:
          // Do nothing.
      }
    }
	//调用上面的重载方法。
    return into(
        glideContext.buildImageViewTarget(view, transcodeClass),
        /*targetListener=*/ null,
        requestOptions,
        Executors.mainThreadExecutor());
  }
```

into(ImageView)方法先判断需不需要对图片进行裁切，然后调用别的into重载方法。对于ImageView默认scaleType为FIT_CENTER情况：

```java
BaseRequestManagerOptions.java
  @NonNull
  @CheckResult
  public T optionalFitCenter() {
    return optionalScaleOnlyTransform(DownsampleStrategy.FIT_CENTER, new FitCenter());
  }

  @NonNull
  private T optionalScaleOnlyTransform(
      @NonNull DownsampleStrategy strategy, @NonNull Transformation<Bitmap> transformation) {
    return scaleOnlyTransform(strategy, transformation, false /*isTransformationRequired*/);
  }

  @SuppressWarnings("unchecked")
  @NonNull
  private T scaleOnlyTransform(
      @NonNull DownsampleStrategy strategy,
      @NonNull Transformation<Bitmap> transformation,
      boolean isTransformationRequired) {
    BaseRequestOptions<T> result = isTransformationRequired
          ? transform(strategy, transformation) : optionalTransform(strategy, transformation);
    result.isScaleOnlyOrNoTransform = true;
    return (T) result;
  }

  @SuppressWarnings({"WeakerAccess", "CheckResult"})
  @NonNull
  final T optionalTransform(@NonNull DownsampleStrategy downsampleStrategy,
      @NonNull Transformation<Bitmap> transformation) {
    if (isAutoCloneEnabled) {
      return clone().optionalTransform(downsampleStrategy, transformation);
    }

    downsample(downsampleStrategy);
    return transform(transformation, /*isRequired=*/ false);
  }

  @NonNull
  @CheckResult
  public T downsample(@NonNull DownsampleStrategy strategy) {
    return set(DownsampleStrategy.OPTION, Preconditions.checkNotNull(strategy));
  }

  @NonNull
  @CheckResult
  public <Y> T set(@NonNull Option<Y> option, @NonNull Y value) {
    if (isAutoCloneEnabled) {
      return clone().set(option, value);
    }

    Preconditions.checkNotNull(option);
    Preconditions.checkNotNull(value);
    options.set(option, value);
    return selfOrThrowIfLocked();
  }

  @NonNull
  T transform(
      @NonNull Transformation<Bitmap> transformation, boolean isRequired) {
    if (isAutoCloneEnabled) {
      return clone().transform(transformation, isRequired);
    }

    DrawableTransformation drawableTransformation =
        new DrawableTransformation(transformation, isRequired);
    transform(Bitmap.class, transformation, isRequired);
    transform(Drawable.class, drawableTransformation, isRequired);
    // TODO: remove BitmapDrawable decoder and this transformation.
    // Registering as BitmapDrawable is simply an optimization to avoid some iteration and
    // isAssignableFrom checks when obtaining the transformation later on. It can be removed without
    // affecting the functionality.
    transform(BitmapDrawable.class, drawableTransformation.asBitmapDrawable(), isRequired);
    transform(GifDrawable.class, new GifDrawableTransformation(transformation), isRequired);
    return selfOrThrowIfLocked();
  }
```

上面的这些操作实际上是将几个值保存到BaseRequestOptions内部的两个CachedHashCodeArrayMap里面，其中键值以及保存的位置如下：

| 保存位置        | K                         | V                                                            |
| --------------- | ------------------------- | ------------------------------------------------------------ |
| Options.values  | DownsampleStrategy.OPTION | DownsampleStrategy.FitCenter()                               |
| transformations | Bitmap.class              | FitCenter()                                                  |
| transformations | Drawable.class            | DrawableTransformation(FitCenter(), false)                   |
| transformations | BitmapDrawable.class      | DrawableTransformation(FitCenter(), false).asBitmapDrawable() |
| transformations | GifDrawable.class         | GifDrawableTransformation(FitCenter())                       |

将KV保存好了之后，就准备调用最终的into方法了。

```java
	into(
        glideContext.buildImageViewTarget(view, transcodeClass),
        /*targetListener=*/ null,
        requestOptions,
        Executors.mainThreadExecutor());
```

第一个参数ViewTarget<ImageView,  transcodeType> 为DrawableImageViewTarget。

```java
GlideContext.java
  @NonNull
  public <X> ViewTarget<ImageView, X> buildImageViewTarget(
      @NonNull ImageView imageView, @NonNull Class<X> transcodeClass) {
    // imageViewTargetFactory是ImageViewTargetFactory的一个实例
    // transcodeClass在RequestManger#load流程确认了。Drawable.class。
    return imageViewTargetFactory.buildTarget(imageView, transcodeClass);
  }
```

```java
ImageViewTargetFactory.java
  @NonNull
  @SuppressWarnings("unchecked")
  public <Z> ViewTarget<ImageView, Z> buildTarget(@NonNull ImageView view,
      @NonNull Class<Z> clazz) {
    if (Bitmap.class.equals(clazz)) {
      return (ViewTarget<ImageView, Z>) new BitmapImageViewTarget(view);
    } else if (Drawable.class.isAssignableFrom(clazz)) {
      // 返回的是(ViewTarget<ImageView, Drawable>) new DrawableImageViewTarget(view);
      return (ViewTarget<ImageView, Z>) new DrawableImageViewTarget(view);
    } else {
      throw new IllegalArgumentException(
          "Unhandled class: " + clazz + ", try .as*(Class).transcode(ResourceTranscoder)");
    }
  }
```

Executors.mainThreadExecutor()就是一个Executor，使用了MainLooper的Handler，在execute方法中通过handler#post(Runnable)来执行提交给线程池的任务。

```java
Executors.java
    private static final Executor MAIN_THREAD_EXECUTOR =
      new Executor() {
        private final Handler handler = new Handler(Looper.getMainLooper());

        @Override
        public void execute(@NonNull Runnable command) {
          handler.post(command);
        }
      };

  public static Executor mainThreadExecutor() {
    return MAIN_THREAD_EXECUTOR;
  }
```

现在分析最终的重载的into方法。

```java
RequestBuilder.java
private <Y extends Target<TranscodeType>> Y into(
    @NonNull Y target,
    @Nullable RequestListener<TranscodeType> targetListener,
    BaseRequestOptions<?> options,
    Executor callbackExecutor) {
  Preconditions.checkNotNull(target);
  if (!isModelSet) {
    throw new IllegalArgumentException("You must call #load() before calling #into()");
  }
  // 创建了一个SingleRequest，见后面️⛰️⛰️⛰️
  Request request = buildRequest(target, targetListener, options, callbackExecutor);
  // 这里会判断需不需要重新开始任务
  // 如果当前request和target上之前的request previous相等；且设置了忽略内存缓存或previous还没有完成
  // 那么会进入if分支，无需进行一些相关设置，这是一个很好的优化
  Request previous = target.getRequest();
  if (request.isEquivalentTo(previous)
      && !isSkipMemoryCacheWithCompletePreviousRequest(options, previous)) {
    request.recycle();
    // If the request is completed, beginning again will ensure the result is re-delivered,
    // triggering RequestListeners and Targets. If the request is failed, beginning again will
    // restart the request, giving it another chance to complete. If the request is already
    // running, we can let it continue running without interruption.
    if (!Preconditions.checkNotNull(previous).isRunning()) {
      // Use the previous request rather than the new one to allow for optimizations like skipping
      // setting placeholders, tracking and un-tracking Targets, and obtaining View dimensions
      // that are done in the individual Request.
      previous.begin();
    }
    return target;
  }
  // 如果不能复用previous
  // 先清除target上之前的Request
  requestManager.clear(target);
  // 将Request作为tag设置到view中
  target.setRequest(request);
  // 😷😷😷 真正开始网络图片的加载
  requestManager.track(target, request);

  return target;
}
```

### 3.3.1 buildRequest

跟踪一下buildRequest的流程，看看是如何创建出SingleRequest的。

```java
RequestBuilder.java
  private Request buildRequest(
      Target<TranscodeType> target,
      @Nullable RequestListener<TranscodeType> targetListener,
      BaseRequestOptions<?> requestOptions,
      Executor callbackExecutor) {
    return buildRequestRecursive(
        target,
        targetListener,
        /*parentCoordinator=*/ null,
        transitionOptions,
        requestOptions.getPriority(),
        requestOptions.getOverrideWidth(),
        requestOptions.getOverrideHeight(),
        requestOptions,
        callbackExecutor);
  }

  private Request buildRequestRecursive(
      Target<TranscodeType> target,
      @Nullable RequestListener<TranscodeType> targetListener,
      @Nullable RequestCoordinator parentCoordinator,
      TransitionOptions<?, ? super TranscodeType> transitionOptions,
      Priority priority,
      int overrideWidth,
      int overrideHeight,
      BaseRequestOptions<?> requestOptions,
      Executor callbackExecutor) {

    // Build the ErrorRequestCoordinator first if necessary so we can update parentCoordinator.
    ErrorRequestCoordinator errorRequestCoordinator = null;
    // errorBuilder为null，skip 因此errorRequestCoordinator为null。
    if (errorBuilder != null) {
      errorRequestCoordinator = new ErrorRequestCoordinator(parentCoordinator);
      parentCoordinator = errorRequestCoordinator;
    }
	// 构建SingleRequest。
    Request mainRequest =
        buildThumbnailRequestRecursive(
            target,
            targetListener,
            parentCoordinator,
            transitionOptions,
            priority,
            overrideWidth,
            overrideHeight,
            requestOptions,
            callbackExecutor);
	// errorRequestCoordinator为null。
    if (errorRequestCoordinator == null) {
      return mainRequest;
    }
	...
  }

  private Request buildThumbnailRequestRecursive(
      Target<TranscodeType> target,
      RequestListener<TranscodeType> targetListener,
      @Nullable RequestCoordinator parentCoordinator,
      TransitionOptions<?, ? super TranscodeType> transitionOptions,
      Priority priority,
      int overrideWidth,
      int overrideHeight,
      BaseRequestOptions<?> requestOptions,
      Executor callbackExecutor) {
    // thumbnail重载方法没有调用过，thumbnailBuilder为null，thumbSizeMultiplier为null。
      
    if (thumbnailBuilder != null) {
		...
    } else if (thumbSizeMultiplier != null) {
       	...
    } else {
      // Base case: no thumbnail.
      return obtainRequest(
          target,
          targetListener,
          requestOptions,
          parentCoordinator,
          transitionOptions,
          priority,
          overrideWidth,
          overrideHeight,
          callbackExecutor);
    }
  }

  private Request obtainRequest(
      Target<TranscodeType> target,
      RequestListener<TranscodeType> targetListener,
      BaseRequestOptions<?> requestOptions,
      RequestCoordinator requestCoordinator,
      TransitionOptions<?, ? super TranscodeType> transitionOptions,
      Priority priority,
      int overrideWidth,
      int overrideHeight,
      Executor callbackExecutor) {
    return SingleRequest.obtain(
        context,
        glideContext,
        model,
        transcodeClass,
        requestOptions,
        overrideWidth,
        overrideHeight,
        priority,
        target,
        targetListener,
        requestListeners,
        requestCoordinator,
        glideContext.getEngine(),
        transitionOptions.getTransitionFactory(),
        callbackExecutor);
  }
```

SingleRequest的初始状态为Status.PENDING。

### 3.3.2 RequestManager#track

接着分析RequestManager#track流程。

```java
RequestManager.java
  synchronized void track(@NonNull Target<?> target, @NonNull Request request) {
    targetTracker.track(target);
    requestTracker.runRequest(request);
  }
```

TargetTracker作用是保存所有的Target并向它们转发生命周期事件。RequestTracker的作用是管理所有状态的请求。

targetTracker.tack(target)将target保存到内部的targets中：

```java
TargetTracker.java
private final Set<Target<?>> targets = Collections.newSetFromMap(new WeakHashMap<Target<?>, Boolean>());

  public void track(@NonNull Target<?> target) {
    targets.add(target);
  }
```

```java
RequestTracker.java
private final Set<Request> requests =
      Collections.newSetFromMap(new WeakHashMap<Request, Boolean>());
  public void runRequest(@NonNull Request request) {
    requests.add(request);
    if (!isPaused) {
      request.begin();
    } else {
      request.clear();
      if (Log.isLoggable(TAG, Log.VERBOSE)) {
        Log.v(TAG, "Paused, delaying request");
      }
      pendingRequests.add(request);
    }
  }
```

将request添加到内部的requests中。isPaused默认为false，只有调用RequestTracker#pauseRequests或RequestTracker#pauseAllRequests后才会为true。

这里的request是上文讲的SingleRequest。分析一下SingleRequest#begin方法。

```java
SingleRequest.java
  @Override
  public synchronized void begin() {
    assertNotCallingCallbacks();
    stateVerifier.throwIfRecycled();
    startTime = LogTime.getLogTime();
    // 1 如果model为null，会调用监听器的onLoadFailed处理。若无法处理，则展示失败时的占位图。
    if (model == null) {
      if (Util.isValidDimensions(overrideWidth, overrideHeight)) {
        width = overrideWidth;
        height = overrideHeight;
      }
      // Only log at more verbose log levels if the user has set a fallback drawable, because
      // fallback Drawables indicate the user expects null models occasionally.
      int logLevel = getFallbackDrawable() == null ? Log.WARN : Log.DEBUG;
      onLoadFailed(new GlideException("Received null model"), logLevel);
      return;
    }

    if (status == Status.RUNNING) {
      throw new IllegalArgumentException("Cannot restart a running request");
    }

    // If we're restarted after we're complete (usually via something like a notifyDataSetChanged
    // that starts an identical request into the same Target or View), we can simply use the
    // resource and size we retrieved the last time around and skip obtaining a new size, starting a
    // new load etc. This does mean that users who want to restart a load because they expect that
    // the view size has changed will need to explicitly clear the View or Target before starting
    // the new load.
    // 2 如果我们在请求完成后，重新加载，那么就返回已经加载好的资源。由于View的尺寸的改变，我们的确需要重新来加载，此时
    // 我们需要明确地清除View或Target。
    if (status == Status.COMPLETE) {
      onResourceReady(resource, DataSource.MEMORY_CACHE);
      return;
    }

    // Restarts for requests that are neither complete nor running can be treated as new requests
    // and can run again from the beginning.
	//请求即没有完成，也没有运行中，则视作新的请求。
    //3 如果指定了overrideWidh和overrideHeight，那么直接调用onSizeReady方法，否则会获取ImageView的宽、高，然后调用
    // onSizeReady方法，在该方法中会创建图片加载的Job并开始运行。
    status = Status.WAITING_FOR_SIZE;
    if (Util.isValidDimensions(overrideWidth, overrideHeight)) {
      onSizeReady(overrideWidth, overrideHeight);
    } else {
      target.getSize(this);
    }

    //4 显示加载中的占位符。
    if ((status == Status.RUNNING || status == Status.WAITING_FOR_SIZE)
        && canNotifyStatusChanged()) {
      target.onLoadStarted(getPlaceholderDrawable());
    }
    if (IS_VERBOSE_LOGGABLE) {
      logV("finished run method in " + LogTime.getElapsedMillis(startTime));
    }
  }
```

先看一下model = null时，onLoadFailed(new GlideException("Received null model"), logLevel)；干了什么：

```java
SingleRequest.java
  private synchronized void onLoadFailed(GlideException e, int maxLogLevel) {
    stateVerifier.throwIfRecycled();
    e.setOrigin(requestOrigin);
    int logLevel = glideContext.getLogLevel();
    if (logLevel <= maxLogLevel) {
      Log.w(GLIDE_TAG, "Load failed for " + model + " with size [" + width + "x" + height + "]", e);
      if (logLevel <= Log.INFO) {
        e.logRootCauses(GLIDE_TAG);
      }
    }

    loadStatus = null;
    // 设置状态为Status.FAILED。
    status = Status.FAILED;

    isCallingCallbacks = true;
    try {
      //TODO: what if this is a thumbnail request?
      // 调用各个listener的onLoadFailed处理。
      boolean anyListenerHandledUpdatingTarget = false;
      if (requestListeners != null) {
        for (RequestListener<R> listener : requestListeners) {
          anyListenerHandledUpdatingTarget |=
              listener.onLoadFailed(e, model, target, isFirstReadyResource());
        }
      }
      anyListenerHandledUpdatingTarget |=
          targetListener != null
              && targetListener.onLoadFailed(e, model, target, isFirstReadyResource());
		// 如果没有一个listener处理则调用setErrorPlaceHolder。默认时不处理的。
      if (!anyListenerHandledUpdatingTarget) {
        setErrorPlaceholder();
      }
    } finally {
      isCallingCallbacks = false;
    }

    // 通知requestCoordinator，请求失败。
    notifyLoadFailed();
  }

// 加载失败显示占位符的逻辑。
  private synchronized void setErrorPlaceholder() {
    if (!canNotifyStatusChanged()) {
      return;
    }

    Drawable error = null;
    if (model == null) {
      error = getFallbackDrawable();
    }
    // Either the model isn't null, or there was no fallback drawable set.
    if (error == null) {
      error = getErrorDrawable();
    }
    // The model isn't null, no fallback drawable was set or no error drawable was set.
    if (error == null) {
      error = getPlaceholderDrawable();
    }
    target.onLoadFailed(error);
  }
```

这里target是DrawableImageViewTarget类型，onLoadFailed方法的实现是在其父类ImageViewTarget中：

```java
ImageViewTarget.java
  @Override
  public void onLoadFailed(@Nullable Drawable errorDrawable) {
    super.onLoadFailed(errorDrawable);
    setResourceInternal(null);
    setDrawable(errorDrawable);
  }

  @Override
  public void setDrawable(Drawable drawable) {
    view.setImageDrawable(drawable);
  }
```

当model为null时，显示逻辑如下：

(1) 如果设置了fallback，那么显示fallback；

(2) 如果设置了error，那么显示error；

(3) 如果设置了placeholder，那么显示placeholder。

这和之前分析的图片加载失败model = null时占位符显示的逻辑是一致的。

回到SingleRequest#begin方法中，判断完model为null后，接着判断status是否为Status.COMPLETED，如果是，会调用onResourceReady(resource, DataSource.MEMORY_CACHE)并返回。接着判断是否设置了overrideWidth和overrideHeight，如果设置了，则调用onSizeReady方法，如果没有设置则通过Target#getSize获取ImageView的尺寸，然后调用onSizeReady方法。

```java
SingelRequest.java
  public synchronized void onSizeReady(int width, int height) {
    stateVerifier.throwIfRecycled();
    if (IS_VERBOSE_LOGGABLE) {
      logV("Got onSizeReady in " + LogTime.getElapsedMillis(startTime));
    }
    // 在SingelRequest#begin方法中已经设置status为Status.WAITING_FOR_SIZE了。
    if (status != Status.WAITING_FOR_SIZE) {
      return;
    }
    // 设置status为Status.RUNNING。
    status = Status.RUNNING;
	// 根据尺寸缩小因子得到缩小后的尺寸。
    float sizeMultiplier = requestOptions.getSizeMultiplier();
    this.width = maybeApplySizeMultiplier(width, sizeMultiplier);
    this.height = maybeApplySizeMultiplier(height, sizeMultiplier);

    if (IS_VERBOSE_LOGGABLE) {
      logV("finished setup for calling load in " + LogTime.getElapsedMillis(startTime));
    }
    //根据load里面的参数开始加载。
    loadStatus =
        engine.load(
            glideContext,
            model,
            requestOptions.getSignature(),
            this.width,
            this.height,
            requestOptions.getResourceClass(),
            transcodeClass,
            priority,
            requestOptions.getDiskCacheStrategy(),
            requestOptions.getTransformations(),
            requestOptions.isTransformationRequired(),
            requestOptions.isScaleOnlyOrNoTransform(),
            requestOptions.getOptions(),
            requestOptions.isMemoryCacheable(),
            requestOptions.getUseUnlimitedSourceGeneratorsPool(),
            requestOptions.getUseAnimationPool(),
            requestOptions.getOnlyRetrieveFromCache(),
            this,
            callbackExecutor);

    // This is a hack that's only useful for testing right now where loads complete synchronously
    // even though under any executor running on any thread but the main thread, the load would
    // have completed asynchronously.
    if (status != Status.RUNNING) {
      loadStatus = null;
    }
    if (IS_VERBOSE_LOGGABLE) {
      logV("finished onSizeReady in " + LogTime.getElapsedMillis(startTime));
    }
  }
```

### 3.3.3 Engine#load

Engine是负责启动加载和管理active、cached资源的类。在Glide#with流程中通过GlideBuilder#build构建Glide的单例时，创建了Engine对象。

```java
GlideBuilder.java
  @NonNull
  Glide build(@NonNull Context context) {
    if (sourceExecutor == null) {
      sourceExecutor = GlideExecutor.newSourceExecutor();
    }

    if (diskCacheExecutor == null) {
      diskCacheExecutor = GlideExecutor.newDiskCacheExecutor();
    }

    if (animationExecutor == null) {
      animationExecutor = GlideExecutor.newAnimationExecutor();
    }

    if (memorySizeCalculator == null) {
      memorySizeCalculator = new MemorySizeCalculator.Builder(context).build();
    }

    if (connectivityMonitorFactory == null) {
      connectivityMonitorFactory = new DefaultConnectivityMonitorFactory();
    }

    if (bitmapPool == null) {
      int size = memorySizeCalculator.getBitmapPoolSize();
      if (size > 0) {
        bitmapPool = new LruBitmapPool(size);
      } else {
        bitmapPool = new BitmapPoolAdapter();
      }
    }

    if (arrayPool == null) {
      arrayPool = new LruArrayPool(memorySizeCalculator.getArrayPoolSizeInBytes());
    }

    if (memoryCache == null) {
      memoryCache = new LruResourceCache(memorySizeCalculator.getMemoryCacheSize());
    }

    if (diskCacheFactory == null) {
      diskCacheFactory = new InternalCacheDiskCacheFactory(context);
    }

    if (engine == null) {
      engine =
          new Engine(
              memoryCache,
              diskCacheFactory,
              diskCacheExecutor,
              sourceExecutor,
              GlideExecutor.newUnlimitedSourceExecutor(),
              GlideExecutor.newAnimationExecutor(),
              isActiveResourceRetentionAllowed);
    }

    if (defaultRequestListeners == null) {
      defaultRequestListeners = Collections.emptyList();
    } else {
      defaultRequestListeners = Collections.unmodifiableList(defaultRequestListeners);
    }

    RequestManagerRetriever requestManagerRetriever =
        new RequestManagerRetriever(requestManagerFactory);

    return new Glide(
        context,
        engine,
        memoryCache,
        bitmapPool,
        arrayPool,
        requestManagerRetriever,
        connectivityMonitorFactory,
        logLevel,
        defaultRequestOptions.lock(),
        defaultTransitionOptions,
        defaultRequestListeners,
        isLoggingRequestOriginsEnabled);
  }
```

Engine#load方法，首先会以RequestOptions的属性构建EngineKey，接着根据EngineKey依次从active资源、chaced资源中寻找资源。若没有找到。根据EngineKey在当前进行的加载集合中寻找EngineJob；如果没有找到进行中的EngineJob，则会创建对应EngineJob并开始执行。

活动资源是已提供给至少一个请求并且尚未被释放的资源。一旦所有消费者释放了资源，该资源就会放入cache中。如果缓存中的资源提供给了新的消费者，它会被重新添加到active资源中。如果一个资源从缓存中移除，其内部拥有的资源将会回收或者在可能的情况下重用。并没有严格要求请求一定要释放资源，所以active资源会以弱引用的方式保持。

```java
Engine.java
  public synchronized <R> LoadStatus load(
      GlideContext glideContext,
      Object model,
      Key signature,
      int width,
      int height,
      Class<?> resourceClass,
      Class<R> transcodeClass,
      Priority priority,
      DiskCacheStrategy diskCacheStrategy,
      Map<Class<?>, Transformation<?>> transformations,
      boolean isTransformationRequired,
      boolean isScaleOnlyOrNoTransform,
      Options options,
      boolean isMemoryCacheable,
      boolean useUnlimitedSourceExecutorPool,
      boolean useAnimationPool,
      boolean onlyRetrieveFromCache,
      ResourceCallback cb,
      Executor callbackExecutor) {
    long startTime = VERBOSE_IS_LOGGABLE ? LogTime.getLogTime() : 0;
	//1 根据RequestOptions的属性构建EngineKey。
    EngineKey key = keyFactory.buildKey(model, signature, width, height, transformations,
        resourceClass, transcodeClass, options);
	// 2 根据EngineKey从active资源中寻找，第一次是找不到的。
    EngineResource<?> active = loadFromActiveResources(key, isMemoryCacheable);
    if (active != null) {
      cb.onResourceReady(active, DataSource.MEMORY_CACHE);
      if (VERBOSE_IS_LOGGABLE) {
        logWithTimeAndKey("Loaded resource from active resources", startTime, key);
      }
      return null;
    }

    // 3 根据EngineKey从缓存资源中寻找资源，第一次也是找不到的。
    EngineResource<?> cached = loadFromCache(key, isMemoryCacheable);
    if (cached != null) {
      cb.onResourceReady(cached, DataSource.MEMORY_CACHE);
      if (VERBOSE_IS_LOGGABLE) {
        logWithTimeAndKey("Loaded resource from cache", startTime, key);
      }
      return null;
    }

    //3 根据EngineKey在当前正在运行的EngineJob 集合中找EngineJob，第一次是找不到的。
    EngineJob<?> current = jobs.get(key, onlyRetrieveFromCache);
    if (current != null) {
      current.addCallback(cb, callbackExecutor);
      if (VERBOSE_IS_LOGGABLE) {
        logWithTimeAndKey("Added to existing load", startTime, key);
      }
      return new LoadStatus(cb, current);
    }

    //4 构建一个EngineJob。
    EngineJob<R> engineJob =
        engineJobFactory.build(
            key,
            isMemoryCacheable,
            useUnlimitedSourceExecutorPool,
            useAnimationPool,
            onlyRetrieveFromCache);
	//5 构建一个DecodeJob，该类实现了Runnable，
    DecodeJob<R> decodeJob =
        decodeJobFactory.build(
            glideContext,
            model,
            key,
            signature,
            width,
            height,
            resourceClass,
            transcodeClass,
            priority,
            diskCacheStrategy,
            transformations,
            isTransformationRequired,
            isScaleOnlyOrNoTransform,
            onlyRetrieveFromCache,
            options,
            engineJob);
	//6 保存EngineJob。
    jobs.put(key, engineJob);
	//7 添加资源加载状态的回调。
    engineJob.addCallback(cb, callbackExecutor);
    //8 开始执行DecodeJob任务。
    engineJob.start(decodeJob);

    if (VERBOSE_IS_LOGGABLE) {
      logWithTimeAndKey("Started new load", startTime, key);
    }
    return new LoadStatus(cb, engineJob);
  }
```

engineJobFactory与decodeJobFactory使用了对象池。以DecodeJobFactory为例：

```java
DecodeJobFactory.java
  @VisibleForTesting
  static class DecodeJobFactory {
    @Synthetic final DecodeJob.DiskCacheProvider diskCacheProvider;
    @Synthetic final Pools.Pool<DecodeJob<?>> pool =
        FactoryPools.threadSafe(JOB_POOL_SIZE,
            new FactoryPools.Factory<DecodeJob<?>>() {
          @Override
          public DecodeJob<?> create() {
            return new DecodeJob<>(diskCacheProvider, pool);
          }
        });
    private int creationOrder;

    DecodeJobFactory(DecodeJob.DiskCacheProvider diskCacheProvider) {
      this.diskCacheProvider = diskCacheProvider;
    }

    @SuppressWarnings("unchecked")
    <R> DecodeJob<R> build(GlideContext glideContext,
        Object model,
        EngineKey loadKey,
        Key signature,
        int width,
        int height,
        Class<?> resourceClass,
        Class<R> transcodeClass,
        Priority priority,
        DiskCacheStrategy diskCacheStrategy,
        Map<Class<?>, Transformation<?>> transformations,
        boolean isTransformationRequired,
        boolean isScaleOnlyOrNoTransform,
        boolean onlyRetrieveFromCache,
        Options options,
        DecodeJob.Callback<R> callback) {
      DecodeJob<R> result = Preconditions.checkNotNull((DecodeJob<R>) pool.acquire());
      return result.init(
          glideContext,
          model,
          loadKey,
          signature,
          width,
          height,
          resourceClass,
          transcodeClass,
          priority,
          diskCacheStrategy,
          transformations,
          isTransformationRequired,
          isScaleOnlyOrNoTransform,
          onlyRetrieveFromCache,
          options,
          callback,
          creationOrder++);
    }
  }
```

```java
FactoryPools.java
  @NonNull
  public static <T extends Poolable> Pool<T> threadSafe(int size, @NonNull Factory<T> factory) {
    return build(new SynchronizedPool<T>(size), factory);
  }

  @NonNull
  private static <T extends Poolable> Pool<T> build(@NonNull Pool<T> pool,
      @NonNull Factory<T> factory) {
    return build(pool, factory, FactoryPools.<T>emptyResetter());
  }

  @NonNull
  private static <T> Pool<T> build(@NonNull Pool<T> pool, @NonNull Factory<T> factory,
      @NonNull Resetter<T> resetter) {
    return new FactoryPool<>(pool, factory, resetter);
  }

  private static final class FactoryPool<T> implements Pool<T> {
    private final Factory<T> factory;
    private final Resetter<T> resetter;
    private final Pool<T> pool;

    FactoryPool(@NonNull Pool<T> pool, @NonNull Factory<T> factory, @NonNull Resetter<T> resetter) {
      this.pool = pool;
      this.factory = factory;
      this.resetter = resetter;
    }

    @Override
    public T acquire() {
      T result = pool.acquire();
      if (result == null) {
        result = factory.create();
        if (Log.isLoggable(TAG, Log.VERBOSE)) {
          Log.v(TAG, "Created new " + result.getClass());
        }
      }
      if (result instanceof Poolable) {
        ((Poolable) result).getVerifier().setRecycled(false /*isRecycled*/);
      }
      return result;
    }

    @Override
    public boolean release(@NonNull T instance) {
      if (instance instanceof Poolable) {
        ((Poolable) instance).getVerifier().setRecycled(true /*isRecycled*/);
      }
      resetter.reset(instance);
      return pool.release(instance);
    }
  }
```

```java
android.support.v4.util.Pools
public final class Pools {
    private Pools() {
    }

    public static class SynchronizedPool<T> extends Pools.SimplePool<T> {
        private final Object mLock = new Object();

        public SynchronizedPool(int maxPoolSize) {
            super(maxPoolSize);
        }

        public T acquire() {
            synchronized(this.mLock) {
                return super.acquire();
            }
        }

        public boolean release(@NonNull T element) {
            synchronized(this.mLock) {
                return super.release(element);
            }
        }
    }

    public static class SimplePool<T> implements Pools.Pool<T> {
        private final Object[] mPool;
        private int mPoolSize;

        public SimplePool(int maxPoolSize) {
            if (maxPoolSize <= 0) {
                throw new IllegalArgumentException("The max pool size must be > 0");
            } else {
                this.mPool = new Object[maxPoolSize];
            }
        }

        public T acquire() {
            if (this.mPoolSize > 0) {
                int lastPooledIndex = this.mPoolSize - 1;
                T instance = this.mPool[lastPooledIndex];
                this.mPool[lastPooledIndex] = null;
                --this.mPoolSize;
                return instance;
            } else {
                return null;
            }
        }

        public boolean release(@NonNull T instance) {
            if (this.isInPool(instance)) {
                throw new IllegalStateException("Already in the pool!");
            } else if (this.mPoolSize < this.mPool.length) {
                this.mPool[this.mPoolSize] = instance;
                ++this.mPoolSize;
                return true;
            } else {
                return false;
            }
        }

        private boolean isInPool(@NonNull T instance) {
            for(int i = 0; i < this.mPoolSize; ++i) {
                if (this.mPool[i] == instance) {
                    return true;
                }
            }

            return false;
        }
    }

    public interface Pool<T> {
        @Nullable
        T acquire();

        boolean release(@NonNull T var1);
    }
}
```

接着回到Engine#load中EngineJob#start(DecodeJob)

```java
EngineJob.java
    // 在EngineJob的构造方法中实例化了sourceExecotor。
    EngineJob(
      GlideExecutor diskCacheExecutor,
      GlideExecutor sourceExecutor,
      GlideExecutor sourceUnlimitedExecutor,
      GlideExecutor animationExecutor,
      EngineJobListener listener,
      Pools.Pool<EngineJob<?>> pool) {
    this(
        diskCacheExecutor,
        sourceExecutor,
        sourceUnlimitedExecutor,
        animationExecutor,
        listener,
        pool,
        DEFAULT_FACTORY);
  }

  public synchronized void start(DecodeJob<R> decodeJob) {
    this.decodeJob = decodeJob;
    // decodeJob.willDecodeFromCache返回值是false。这里executor是sourceExecutor。
    GlideExecutor executor = decodeJob.willDecodeFromCache()
        ? diskCacheExecutor
        : getActiveSourceExecutor();
    executor.execute(decodeJob);
  }

  private GlideExecutor getActiveSourceExecutor() {
    // 默认的useUnlimitedSourceGeneratorPool、useAnimationPool都为false，这两个变量的值都来源于BaseRequestOptions
    // 因为在加载图片的三步流程中没有设置该参数，所以均为false。得到的是sourceExecutor。
    return useUnlimitedSourceGeneratorPool
        ? sourceUnlimitedExecutor : (useAnimationPool ? animationExecutor : sourceExecutor);
  }
```



```java
  DecodeJob.java
  boolean willDecodeFromCache() {
    Stage firstStage = getNextStage(Stage.INITIALIZE);
    return firstStage == Stage.RESOURCE_CACHE || firstStage == Stage.DATA_CACHE;
  }

  private Stage getNextStage(Stage current) {
    switch (current) {
      case INITIALIZE:
        return diskCacheStrategy.decodeCachedResource()
            ? Stage.RESOURCE_CACHE : getNextStage(Stage.RESOURCE_CACHE);
      case RESOURCE_CACHE:
        return diskCacheStrategy.decodeCachedData()
            ? Stage.DATA_CACHE : getNextStage(Stage.DATA_CACHE);
      case DATA_CACHE:
        // Skip loading from source if the user opted to only retrieve the resource from cache.
        return onlyRetrieveFromCache ? Stage.FINISHED : Stage.SOURCE;
      case SOURCE:
      case FINISHED:
        return Stage.FINISHED;
      default:
        throw new IllegalArgumentException("Unrecognized stage: " + current);
    }
  }

  private enum Stage {
    /** The initial stage. */
    INITIALIZE,
    /** Decode from a cached resource. */
    RESOURCE_CACHE,
    /** Decode from cached source data. */
    DATA_CACHE,
    /** Decode from retrieved source. */
    SOURCE,
    /** Encoding transformed resources after a successful load. */
    ENCODE,
    /** No more viable stages. */
    FINISHED,
  }
```

由于在Glide的加载流程中调用了apply(BaseRequestOptions)

如下代码

```k
        val options = RequestOptions()
            .placeholder(ColorDrawable(Color.GRAY))
            .error(ColorDrawable(Color.RED))
            .fallback(ColorDrawable(Color.CYAN))
            .skipMemoryCache(true)
            .diskCacheStrategy(DiskCacheStrategy.NONE)
        Glide.with(this).load(URL).apply(options).into(ivGlide!!)
```

BaseRequestOptions设置了DiskCacheStrategy.NONE。

```java
DiskCacheStrategy.java
  public static final DiskCacheStrategy NONE = new DiskCacheStrategy() {
    @Override
    public boolean isDataCacheable(DataSource dataSource) {
      return false;
    }

    @Override
    public boolean isResourceCacheable(boolean isFromAlternateCacheKey, DataSource dataSource,
        EncodeStrategy encodeStrategy) {
      return false;
    }

    @Override
    public boolean decodeCachedResource() {
      return false;
    }

    @Override
    public boolean decodeCachedData() {
      return false;
    }
  };
```

所以DecodeJob#willDecodeFromCache返回值为false。

```java
Engine.java
    // 在Engine的构造方法中实例化了soureExecutor。而Engine的构造方法是在GlideBuilder#build创建Glide单例的过程中
    // 创建Engine的实例。
    public Engine(
      MemoryCache memoryCache,
      DiskCache.Factory diskCacheFactory,
      GlideExecutor diskCacheExecutor,
      GlideExecutor sourceExecutor,
      GlideExecutor sourceUnlimitedExecutor,
      GlideExecutor animationExecutor,
      boolean isActiveResourceRetentionAllowed) {
    this(
        memoryCache,
        diskCacheFactory,
        diskCacheExecutor,
        sourceExecutor,
        sourceUnlimitedExecutor,
        animationExecutor,
        /*jobs=*/ null,
        /*keyFactory=*/ null,
        /*activeResources=*/ null,
        /*engineJobFactory=*/ null,
        /*decodeJobFactory=*/ null,
        /*resourceRecycler=*/ null,
        isActiveResourceRetentionAllowed);
  }

  @VisibleForTesting
  static class EngineJobFactory {
    @Synthetic final GlideExecutor diskCacheExecutor;
    @Synthetic final GlideExecutor sourceExecutor;
    @Synthetic final GlideExecutor sourceUnlimitedExecutor;
    @Synthetic final GlideExecutor animationExecutor;
    @Synthetic final EngineJobListener listener;
    @Synthetic final Pools.Pool<EngineJob<?>> pool =
        FactoryPools.threadSafe(
            JOB_POOL_SIZE,
            new FactoryPools.Factory<EngineJob<?>>() {
              @Override
              public EngineJob<?> create() {
                return new EngineJob<>(
                    diskCacheExecutor,
                    sourceExecutor,
                    sourceUnlimitedExecutor,
                    animationExecutor,
                    listener,
                    pool);
              }
            });

    EngineJobFactory(
        GlideExecutor diskCacheExecutor,
        GlideExecutor sourceExecutor,
        GlideExecutor sourceUnlimitedExecutor,
        GlideExecutor animationExecutor,
        EngineJobListener listener) {
      this.diskCacheExecutor = diskCacheExecutor;
      this.sourceExecutor = sourceExecutor;
      this.sourceUnlimitedExecutor = sourceUnlimitedExecutor;
      this.animationExecutor = animationExecutor;
      this.listener = listener;
    }

    @VisibleForTesting
    void shutdown() {
      Executors.shutdownAndAwaitTermination(diskCacheExecutor);
      Executors.shutdownAndAwaitTermination(sourceExecutor);
      Executors.shutdownAndAwaitTermination(sourceUnlimitedExecutor);
      Executors.shutdownAndAwaitTermination(animationExecutor);
    }

    @SuppressWarnings("unchecked")
    <R> EngineJob<R> build(
        Key key,
        boolean isMemoryCacheable,
        boolean useUnlimitedSourceGeneratorPool,
        boolean useAnimationPool,
        boolean onlyRetrieveFromCache) {
      // 构造EngineJ
      EngineJob<R> result = Preconditions.checkNotNull((EngineJob<R>) pool.acquire());
      return result.init(
          key,
          isMemoryCacheable,
          useUnlimitedSourceGeneratorPool,
          useAnimationPool,
          onlyRetrieveFromCache);
    }
  }
```



```java
GlideExecutor.java
  private static final String DEFAULT_SOURCE_EXECUTOR_NAME = "source";
  private static final int MAXIMUM_AUTOMATIC_THREAD_COUNT = 4;

  public static GlideExecutor newSourceExecutor() {
    return newSourceExecutor(
        calculateBestThreadCount(),
        DEFAULT_SOURCE_EXECUTOR_NAME,
        UncaughtThrowableStrategy.DEFAULT);
  }

  @SuppressWarnings("WeakerAccess")
  public static GlideExecutor newSourceExecutor(
      int threadCount, String name, UncaughtThrowableStrategy uncaughtThrowableStrategy) {
    return new GlideExecutor(
        new ThreadPoolExecutor(
            threadCount /* corePoolSize */,
            threadCount /* maximumPoolSize */,
            0 /* keepAliveTime */,
            TimeUnit.MILLISECONDS,
            new PriorityBlockingQueue<Runnable>(),
            new DefaultThreadFactory(name, uncaughtThrowableStrategy, false)));
  }

  public static int calculateBestThreadCount() {
    if (bestThreadCount == 0) {
      bestThreadCount =
          Math.min(MAXIMUM_AUTOMATIC_THREAD_COUNT, RuntimeCompat.availableProcessors());
    }
    return bestThreadCount;
  }
```

以现在市面上手机大都是四核或者八核为例，sourceExecutor是一个固定为4个线程的线程池。

回到EngineJob#start(DecodeJob)方法中，将使用sourceExecutor会执行DecodeJob任务。

## 3.4 DecodeJob#run

```java
DecodJob.java
  @SuppressWarnings("PMD.AvoidRethrowingException")
  @Override
  public void run() {
    // This should be much more fine grained, but since Java's thread pool implementation silently
    // swallows all otherwise fatal exceptions, this will at least make it obvious to developers
    // that something is failing.
    GlideTrace.beginSectionFormat("DecodeJob#run(model=%s)", model);
    // Methods in the try statement can invalidate currentFetcher, so set a local variable here to
    // ensure that the fetcher is cleaned up either way.
    DataFetcher<?> localFetcher = currentFetcher;
    try {
      if (isCancelled) {
        notifyFailed();
        return;
      }
      runWrapped();
    } catch (CallbackException e) {
      // If a callback not controlled by Glide throws an exception, we should avoid the Glide
      // specific debug logic below.
      throw e;
    } catch (Throwable t) {
      // Catch Throwable and not Exception to handle OOMs. Throwables are swallowed by our
      // usage of .submit() in GlideExecutor so we're not silently hiding crashes by doing this. We
      // are however ensuring that our callbacks are always notified when a load fails. Without this
      // notification, uncaught throwables never notify the corresponding callbacks, which can cause
      // loads to silently hang forever, a case that's especially bad for users using Futures on
      // background threads.
      if (Log.isLoggable(TAG, Log.DEBUG)) {
        Log.d(TAG, "DecodeJob threw unexpectedly"
            + ", isCancelled: " + isCancelled
            + ", stage: " + stage, t);
      }
      // When we're encoding we've already notified our callback and it isn't safe to do so again.
      if (stage != Stage.ENCODE) {
        throwables.add(t);
        notifyFailed();
      }
      if (!isCancelled) {
        throw t;
      }
      throw t;
    } finally {
      // Keeping track of the fetcher here and calling cleanup is excessively paranoid, we call
      // close in all cases anyway.
      if (localFetcher != null) {
        localFetcher.cleanup();
      }
      GlideTrace.endSection();
    }
  }
```

里面真正执行的是runWrapped方法。

```java
DecodeJob.java
  private void runWrapped() {
    switch (runReason) {
      case INITIALIZE:
        // 获取当前状态机的状态。
        stage = getNextStage(Stage.INITIALIZE);
        // 根据状态创建相关的DataFetcherGenerator。
        currentGenerator = getNextGenerator();
        runGenerators();
        break;
      case SWITCH_TO_SOURCE_SERVICE:
        runGenerators();
        break;
      case DECODE_DATA:
        decodeFromRetrievedData();
        break;
      default:
        throw new IllegalStateException("Unrecognized run reason: " + runReason);
    }
    
  private DataFetcherGenerator getNextGenerator() {
    switch (stage) {
      case RESOURCE_CACHE:
        return new ResourceCacheGenerator(decodeHelper, this);
      case DATA_CACHE:
        return new DataCacheGenerator(decodeHelper, this);
      case SOURCE:
        return new SourceGenerator(decodeHelper, this);
      case FINISHED:
        return null;
      default:
        throw new IllegalStateException("Unrecognized stage: " + stage);
    }
  }
    
  private void runGenerators() {
    currentThread = Thread.currentThread();
    startFetchTime = LogTime.getLogTime();
    boolean isStarted = false;
    while (!isCancelled && currentGenerator != null
        && !(isStarted = currentGenerator.startNext())) {
      stage = getNextStage(stage);
      currentGenerator = getNextGenerator();

      if (stage == Stage.SOURCE) {
        reschedule();
        return;
      }
    }
    // We've run out of stages and generators, give up.
    if ((stage == Stage.FINISHED || isCancelled) && !isStarted) {
      notifyFailed();
    }

    // Otherwise a generator started a new load and we expect to be called back in
    // onDataFetcherReady.
  }
```

方法runGenerators中调用相关的DataFetcherGenerator#startNext方法尝试fetch数据。如果状态抵达到了Stage.FINISHED或者job取消了，并且DataFetcherGenerator#startNext返回是false，则调用SingleRequest#onLoadFailed进行错误的处理。

这里共有三个DataFetcherGenerator，依次是：

(1) ResourceCacheGernator。获取采样后、转换后的资源文件的缓存。

(2) DataCacheGenerator。获取原始资源文件的缓存。

(3) SourceGenerator。获取原始资源

这里fetch数据逻辑有点复杂，因为涉及到Registry类，该类是用来管理Glide注册进来的用来扩展或替代Glide默认加载、解码、编码逻辑的组件。

先熟悉Registry类各个组件的功能。

## 3.5 Regitry

## 3.6 ResourceCacheGenerator

未完

# 4 深入探究Glide缓存机制

## 4.1 Glide缓存机制

Glide缓存机制的流程图：

![glide-cache-flow-chart](C:\Users\wangjie\Desktop\study\Glide\imgs\glide-cache-flow-chart.png)

默认情况下，Glide在开始一个新的图片请求之前检查以下多级缓存；

(1) 活动资源（Active Resource）- 现在是否有另一个View正在展示这张图片？

(2) 内存缓存（Memory Cache）- 该图片是否最近被加载并仍存在内存中？

(3) 资源类型 （Resource）- 该图片是否之前曾被解码、转换并写入过磁盘缓存？

(3) 数据来源（Data）- 获取此图片的数据是否之前曾被写入过磁盘缓存？

前两步检查图片是否在内存中，如果是则直接返回图片。后两步则检查图片是否在磁盘上，如果是则异步返回图片。

如果四个步骤都没能找到图片，则Glide返回到原始资源取回数据（原始文件、Uri、Url等）。

Glide的memory cache和disk cache在Glide创建的时候就确定了。代码在GlideBuilder#build(Context)方法里。

```java
GlideBuilder.java
  @NonNull
  Glide build(@NonNull Context context) {
	...
    if (memoryCache == null) {
      memoryCache = new LruResourceCache(memorySizeCalculator.getMemoryCacheSize());
    }

    if (diskCacheFactory == null) {
      diskCacheFactory = new InternalCacheDiskCacheFactory(context);
    }

    if (engine == null) {
      engine =
          new Engine(
              memoryCache,
              diskCacheFactory,
              diskCacheExecutor,
              sourceExecutor,
              GlideExecutor.newUnlimitedSourceExecutor(),
              GlideExecutor.newAnimationExecutor(),
              isActiveResourceRetentionAllowed);
    }
	...
    return new Glide(
        context,
        engine,
        memoryCache,
        bitmapPool,
        arrayPool,
        requestManagerRetriever,
        connectivityMonitorFactory,
        logLevel,
        defaultRequestOptions.lock(),
        defaultTransitionOptions,
        defaultRequestListeners,
        isLoggingRequestOriginsEnabled);
  }
```

memoryCache和diskCacheFactory如果没有在任何GlideModle中进行设置的话，会有一个默认的实现。大部分情况下，使用这个默认实现就很好了。

## 4.2 memoryCache介绍

memoryCache的存取操作发生在Engine中，但是memoryCache还被Glide实例持有。这是因为Glide实现了ComponentCallback2接口，在Glide实例创建完成后，就注册了该接口。这样在内存紧张的时候，可以通知memoryCache释放内存。

```java
Glide.java
  public void trimMemory(int level) {
    // Engine asserts this anyway when removing resources, fail faster and consistently
    Util.assertMainThread();
    // memory cache needs to be trimmed before bitmap pool to trim re-pooled Bitmaps too. See #687.
    memoryCache.trimMemory(level);
    bitmapPool.trimMemory(level);
    arrayPool.trimMemory(level);
  }

  @Override
  public void onTrimMemory(int level) {
    trimMemory(level);
  }
```

memoryCache是一个使用LRU（latest recently used）算法实现的内存缓存类LruResourceCache，继承LruCache类，实现MemoryCache接口。LruCache定义了LRU相关的操作，MemoryCache定义的是内存缓存相关的操作。

LruMemoryCache相关代码如下：

```java
public class LruResourceCache extends LruCache<Key, Resource<?>> implements MemoryCache {

  @Override
  protected int getSize(@Nullable Resource<?> item) {
    if (item == null) {
      return super.getSize(null);
    } else {
      return item.getSize();
    }
  }
    
  @SuppressLint("InlinedApi")
  @Override
  public void trimMemory(int level) {
    if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) {
      // Entering list of cached background apps
      // Evict our entire bitmap cache
      clearMemory();
    } else if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN
        || level == android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL) {
      // The app's UI is no longer visible, or app is in the foreground but system is running
      // critically low on memory
      // Evict oldest half of our bitmap cache
      trimToSize(getMaxSize() / 2);
    }
  }
}
```

LruCache的实现主要依靠LinkedHashMap的一个构造参数：accessOrder。当该参数为true时，每次调用LinkedHashMap的get(Key)或getOrDefault(key, defaultValue)方法就会触发afterNodeAccess(Object)方法，该方法会将对应的Node移动到链表的末尾。也就是说LinkedHashMap末尾的数据时最近最多使用的。而LruCache清除内存时会调用trimToSize(size)方法，会从头到尾清理。这样LRU的特点就体现出来了。

```java
public class LruCache<T, Y> {
  private final Map<T, Y> cache = new LinkedHashMap<>(100, 0.75f, true);

  public void clearMemory() {
    trimToSize(0);
  }
    
  protected synchronized void trimToSize(long size) {
    Map.Entry<T, Y> last;
    Iterator<Map.Entry<T, Y>> cacheIterator;
    while (currentSize > size) {
      // 从头到尾清理。
      cacheIterator  = cache.entrySet().iterator();
      last = cacheIterator.next();
      final Y toRemove = last.getValue();
      currentSize -= getSize(toRemove);
      final T key = last.getKey();
      cacheIterator.remove();
      onItemEvicted(key, toRemove);
    }
  }
    
  private void evict() {
    trimToSize(maxSize);
  }
}
```

```java
public class LinkedHashMap<K,V>
    extends HashMap<K,V>
    implements Map<K,V>
{
    public LinkedHashMap(int initialCapacity,
                         float loadFactor,
                         boolean accessOrder) {
        super(initialCapacity, loadFactor);
        this.accessOrder = accessOrder;
    }

    public V get(Object key) {
        Node<K,V> e;
        if ((e = getNode(hash(key), key)) == null)
            return null;
        if (accessOrder)
            afterNodeAccess(e);
        return e.value;
    }

    /**
     * {@inheritDoc}
     */
    public V getOrDefault(Object key, V defaultValue) {
       Node<K,V> e;
       if ((e = getNode(hash(key), key)) == null)
           return defaultValue;
       if (accessOrder)
           afterNodeAccess(e);
       return e.value;
    }

    void afterNodeAccess(Node<K,V> e) { // move node to last
        LinkedHashMapEntry<K,V> last;
        if (accessOrder && (last = tail) != e) {
            LinkedHashMapEntry<K,V> p =
                (LinkedHashMapEntry<K,V>)e, b = p.before, a = p.after;
            p.after = null;
            if (b == null)
                head = a;
            else
                b.after = a;
            if (a != null)
                a.before = b;
            else
                last = b;
            if (last == null)
                head = p;
            else {
                p.before = last;
                last.after = p;
            }
            tail = p;
            ++modCount;
        }
    }
}
```

## 4.3 diskCacheFactory

diskCacheFactory就是创建diskCache的Factory，其接口定义如下：

```java
public interface DiskCache {

  /**
   * An interface for lazily creating a disk cache.
   */
  interface Factory {
    /** 250 MB of cache. */
    int DEFAULT_DISK_CACHE_SIZE = 250 * 1024 * 1024;
    String DEFAULT_DISK_CACHE_DIR = "image_manager_disk_cache";

    /** Returns a new disk cache, or {@code null} if no disk cache could be created. */
    @Nullable
    DiskCache build();
  }

  /**
   * An interface to actually write data to a key in the disk cache.
   */
  interface Writer {
    /**
     * Writes data to the file and returns true if the write was successful and should be committed,
     * and false if the write should be aborted.
     *
     * @param file The File the Writer should write to.
     */
    boolean write(@NonNull File file);
  }

  /**
   * Get the cache for the value at the given key.
   *
   * <p> Note - This is potentially dangerous, someone may write a new value to the file at any
   * point in time and we won't know about it. </p>
   *
   * @param key The key in the cache.
   * @return An InputStream representing the data at key at the time get is called.
   */
  @Nullable
  File get(Key key);

  /**
   * Write to a key in the cache. {@link Writer} is used so that the cache implementation can
   * perform actions after the write finishes, like commit (via atomic file rename).
   *
   * @param key    The key to write to.
   * @param writer An interface that will write data given an OutputStream for the key.
   */
  void put(Key key, Writer writer);

  /**
   * Remove the key and value from the cache.
   *
   * @param key The key to remove.
   */
  // Public API.
  @SuppressWarnings("unused")
  void delete(Key key);

  /**
   * Clear the cache.
   */
  void clear();
}
```

可以看出DiskCache.Factory的build方法会创建出一个DiskCache。Glide的默认diskCacheFactory为InternalCacheDiskCacheFactory。

```java
public final class InternalCacheDiskCacheFactory extends DiskLruCacheFactory {

  public InternalCacheDiskCacheFactory(Context context) {
    this(context, DiskCache.Factory.DEFAULT_DISK_CACHE_DIR,
        DiskCache.Factory.DEFAULT_DISK_CACHE_SIZE);
  }

  public InternalCacheDiskCacheFactory(Context context, long diskCacheSize) {
    this(context, DiskCache.Factory.DEFAULT_DISK_CACHE_DIR, diskCacheSize);
  }

  public InternalCacheDiskCacheFactory(final Context context, final String diskCacheName,
                                       long diskCacheSize) {
    super(new CacheDirectoryGetter() {
      @Override
      public File getCacheDirectory() {
        File cacheDirectory = context.getCacheDir();
        if (cacheDirectory == null) {
          return null;
        }
        if (diskCacheName != null) {
          return new File(cacheDirectory, diskCacheName);
        }
        return cacheDirectory;
      }
    }, diskCacheSize);
  }
}
```

由上面接口的定义可以看出，会在本地创建一个大小250M，路径为`/data/data/{package}/cache/image_manager_disk_cache/`的缓存目录。

```java
public class DiskLruCacheFactory implements DiskCache.Factory {
  private final long diskCacheSize;
  private final CacheDirectoryGetter cacheDirectoryGetter;

  /**
   * Interface called out of UI thread to get the cache folder.
   */
  public interface CacheDirectoryGetter {
    File getCacheDirectory();
  }

  public DiskLruCacheFactory(final String diskCacheFolder, long diskCacheSize) {
    this(new CacheDirectoryGetter() {
      @Override
      public File getCacheDirectory() {
        return new File(diskCacheFolder);
      }
    }, diskCacheSize);
  }

  public DiskLruCacheFactory(final String diskCacheFolder, final String diskCacheName,
                             long diskCacheSize) {
    this(new CacheDirectoryGetter() {
      @Override
      public File getCacheDirectory() {
        return new File(diskCacheFolder, diskCacheName);
      }
    }, diskCacheSize);
  }

  /**
   * When using this constructor {@link CacheDirectoryGetter#getCacheDirectory()} will be called out
   * of UI thread, allowing to do I/O access without performance impacts.
   *
   * @param cacheDirectoryGetter Interface called out of UI thread to get the cache folder.
   * @param diskCacheSize        Desired max bytes size for the LRU disk cache.
   */
  // Public API.
  @SuppressWarnings("WeakerAccess")
  public DiskLruCacheFactory(CacheDirectoryGetter cacheDirectoryGetter, long diskCacheSize) {
    this.diskCacheSize = diskCacheSize;
    this.cacheDirectoryGetter = cacheDirectoryGetter;
  }

  @Override
  public DiskCache build() {
    File cacheDir = cacheDirectoryGetter.getCacheDirectory();

    if (cacheDir == null) {
      return null;
    }

    if (!cacheDir.mkdirs() && (!cacheDir.exists() || !cacheDir.isDirectory())) {
      return null;
    }

    return DiskLruCacheWrapper.create(cacheDir, diskCacheSize);
  }
}
```

DiskCacheFacoty#build()方法会返回一个DiskLruCacheWrapper类的实例。

```java
public class DiskLruCacheWrapper implements DiskCache {
  private static final String TAG = "DiskLruCacheWrapper";

  private static final int APP_VERSION = 1;
  private static final int VALUE_COUNT = 1;
  private static DiskLruCacheWrapper wrapper;

  private final SafeKeyGenerator safeKeyGenerator;
  private final File directory;
  private final long maxSize;
  private final DiskCacheWriteLocker writeLocker = new DiskCacheWriteLocker();
  private DiskLruCache diskLruCache;

  /**
   * Get a DiskCache in the given directory and size. If a disk cache has already been created with
   * a different directory and/or size, it will be returned instead and the new arguments will be
   * ignored.
   *
   * @param directory The directory for the disk cache
   * @param maxSize   The max size for the disk cache
   * @return The new disk cache with the given arguments, or the current cache if one already exists
   *
   * @deprecated Use {@link #create(File, long)} to create a new cache with the specified arguments.
   */
  @SuppressWarnings("deprecation")
  @Deprecated
  public static synchronized DiskCache get(File directory, long maxSize) {
    // TODO calling twice with different arguments makes it return the cache for the same
    // directory, it's public!
    if (wrapper == null) {
      wrapper = new DiskLruCacheWrapper(directory, maxSize);
    }
    return wrapper;
  }

  /**
   * Create a new DiskCache in the given directory with a specified max size.
   *
   * @param directory The directory for the disk cache
   * @param maxSize   The max size for the disk cache
   * @return The new disk cache with the given arguments
   */
  @SuppressWarnings("deprecation")
  public static DiskCache create(File directory, long maxSize) {
    return new DiskLruCacheWrapper(directory, maxSize);
  }

  /**
   * @deprecated Do not extend this class.
   */
  @Deprecated
  // Deprecated public API.
  @SuppressWarnings({"WeakerAccess", "DeprecatedIsStillUsed"})
  protected DiskLruCacheWrapper(File directory, long maxSize) {
    this.directory = directory;
    this.maxSize = maxSize;
    this.safeKeyGenerator = new SafeKeyGenerator();
  }

  private synchronized DiskLruCache getDiskCache() throws IOException {
    if (diskLruCache == null) {
      diskLruCache = DiskLruCache.open(directory, APP_VERSION, VALUE_COUNT, maxSize);
    }
    return diskLruCache;
  }

  @Override
  public File get(Key key) {
    String safeKey = safeKeyGenerator.getSafeKey(key);
    if (Log.isLoggable(TAG, Log.VERBOSE)) {
      Log.v(TAG, "Get: Obtained: " + safeKey + " for for Key: " + key);
    }
    File result = null;
    try {
      // It is possible that the there will be a put in between these two gets. If so that shouldn't
      // be a problem because we will always put the same value at the same key so our input streams
      // will still represent the same data.
      final DiskLruCache.Value value = getDiskCache().get(safeKey);
      if (value != null) {
        result = value.getFile(0);
      }
    } catch (IOException e) {
      if (Log.isLoggable(TAG, Log.WARN)) {
        Log.w(TAG, "Unable to get from disk cache", e);
      }
    }
    return result;
  }

  ...
}
```

现在DiskCache的实现准备好了，需要追踪以下factory#build方法。diskCacheFactory在GlideBuilder#build构建Glide的实例过程中构建了Engine并将diskCacheFactory传入Engine中。在Engine的构造方法中会被包装成一个LazyDiskCacheProvider，在被需要的时候调用getDiskCache()方法，这样就会调用factory的build()方法返回一个DiskCache了。

```java
Engine.java
  private static class LazyDiskCacheProvider implements DecodeJob.DiskCacheProvider {

    private final DiskCache.Factory factory;
    private volatile DiskCache diskCache;

    LazyDiskCacheProvider(DiskCache.Factory factory) {
      this.factory = factory;
    }

    @VisibleForTesting
    synchronized void clearDiskCacheIfCreated() {
      if (diskCache == null) {
        return;
      }
      diskCache.clear();
    }

    @Override
    public DiskCache getDiskCache() {
      if (diskCache == null) {
        synchronized (this) {
          if (diskCache == null) {
            diskCache = factory.build();
          }
          if (diskCache == null) {
            diskCache = new DiskCacheAdapter();
          }
        }
      }
      return diskCache;
    }
  }
```

而LazyDiskCacheProvider在Engine后面的初始化流程中传入DecodeJobFactory。在DecodeJobFactory构建DecodeJob也会传进去。DecodeJob自身会保存LazyDiskCacheProvider，在资源加载完毕并展示后，会进行缓存。同时DecodeJob也会在DecodeHelper的初始化的时候，传递进去，供ResourceCacheGenerator、DataCacheGenerator读取缓存，共SourceGenerator写入缓存。

## 4.4 ActiveResources

ActiveResources在Engine的构造方法中被创建。ActiveResources构建完成后，会启动一个后台优先级别线程，在该线程中会调用cleanReferenceQueue()方法一直循环清除ReferenceQueue中将要被GC的Resource。

```java
final class ActiveResources {
  private final boolean isActiveResourceRetentionAllowed;
  private final Executor monitorClearedResourcesExecutor;
  @VisibleForTesting
  final Map<Key, ResourceWeakReference> activeEngineResources = new HashMap<>();
  private final ReferenceQueue<EngineResource<?>> resourceReferenceQueue = new ReferenceQueue<>();

  private ResourceListener listener;

  private volatile boolean isShutdown;
  @Nullable
  private volatile DequeuedResourceCallback cb;

  ActiveResources(boolean isActiveResourceRetentionAllowed) {
    this(
        isActiveResourceRetentionAllowed,
        java.util.concurrent.Executors.newSingleThreadExecutor(
            new ThreadFactory() {
              @Override
              public Thread newThread(@NonNull final Runnable r) {
                return new Thread(
                    new Runnable() {
                      @Override
                      public void run() {
                        Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
                        r.run();
                      }
                    },
                    "glide-active-resources");
              }
            }));
  }

  @VisibleForTesting
  ActiveResources(
      boolean isActiveResourceRetentionAllowed, Executor monitorClearedResourcesExecutor) {
    this.isActiveResourceRetentionAllowed = isActiveResourceRetentionAllowed;
    this.monitorClearedResourcesExecutor = monitorClearedResourcesExecutor;

    monitorClearedResourcesExecutor.execute(
        new Runnable() {
          @Override
          public void run() {
            cleanReferenceQueue();
          }
        });
  }

  void setListener(ResourceListener listener) {
    synchronized (listener) {
      synchronized (this) {
        this.listener = listener;
      }
    }
  }

  // ActiveResources的保存。
  synchronized void activate(Key key, EngineResource<?> resource) {
    ResourceWeakReference toPut =
        new ResourceWeakReference(
            key, resource, resourceReferenceQueue, isActiveResourceRetentionAllowed);

    ResourceWeakReference removed = activeEngineResources.put(key, toPut);
    if (removed != null) {
      removed.reset();
    }
  }
  // ActiveResources的删除。
  synchronized void deactivate(Key key) {
    ResourceWeakReference removed = activeEngineResources.remove(key);
    if (removed != null) {
      removed.reset();
    }
  }

  @Nullable
  synchronized EngineResource<?> get(Key key) {
    ResourceWeakReference activeRef = activeEngineResources.get(key);
    if (activeRef == null) {
      return null;
    }

    EngineResource<?> active = activeRef.get();
    if (active == null) {
      cleanupActiveReference(activeRef);
    }
    return active;
  }

  @SuppressWarnings({"WeakerAccess", "SynchronizeOnNonFinalField"})
  @Synthetic
  void cleanupActiveReference(@NonNull ResourceWeakReference ref) {
    // Fixes a deadlock where we normally acquire the Engine lock and then the ActiveResources lock
    // but reverse that order in this one particular test. This is definitely a bit of a hack...
    synchronized (listener) {
      synchronized (this) {
        activeEngineResources.remove(ref.key);
		// 如果是GC后调用，此时ref.resource肯定为null
        if (!ref.isCacheable || ref.resource == null) {
          return;
        }
        // 走到这，表示是在get方法中被调用，此时会恢复原来的resource
        EngineResource<?> newResource =
            new EngineResource<>(ref.resource, /*isCacheable=*/ true, /*isRecyclable=*/ false);
        // 回调Engine的onResourceReleased方法
        // 这会导致此资源从active变成memory cache状态
        newResource.setResourceListener(ref.key, listener);
        listener.onResourceReleased(ref.key, newResource);
      }
    }
  }

  @SuppressWarnings("WeakerAccess")
  @Synthetic void cleanReferenceQueue() {
    while (!isShutdown) {
      try {
        ResourceWeakReference ref = (ResourceWeakReference) resourceReferenceQueue.remove();
        cleanupActiveReference(ref);

        // This section for testing only.
        DequeuedResourceCallback current = cb;
        if (current != null) {
          current.onResourceDequeued();
        }
        // End for testing only.
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
  }

  @VisibleForTesting
  void setDequeuedResourceCallback(DequeuedResourceCallback cb) {
    this.cb = cb;
  }

  @VisibleForTesting
  interface DequeuedResourceCallback {
    void onResourceDequeued();
  }

  @VisibleForTesting
  void shutdown() {
    isShutdown = true;
    if (monitorClearedResourcesExecutor instanceof ExecutorService) {
      ExecutorService service = (ExecutorService) monitorClearedResourcesExecutor;
      Executors.shutdownAndAwaitTermination(service);
    }
  }

  @VisibleForTesting
  static final class ResourceWeakReference extends WeakReference<EngineResource<?>> {
    @SuppressWarnings("WeakerAccess") @Synthetic final Key key;
    @SuppressWarnings("WeakerAccess") @Synthetic final boolean isCacheable;

    @Nullable @SuppressWarnings("WeakerAccess") @Synthetic Resource<?> resource;

    @Synthetic
    @SuppressWarnings("WeakerAccess")
    ResourceWeakReference(
        @NonNull Key key,
        @NonNull EngineResource<?> referent,
        @NonNull ReferenceQueue<? super EngineResource<?>> queue,
        boolean isActiveResourceRetentionAllowed) {
      super(referent, queue);
      this.key = Preconditions.checkNotNull(key);
      this.resource =
          referent.isCacheable() && isActiveResourceRetentionAllowed
              ? Preconditions.checkNotNull(referent.getResource()) : null;
      isCacheable = referent.isCacheable();
    }

    void reset() {
      resource = null;
      clear();
    }
  }
}
```

ActiveResources中的active、deactive分别时保存和删除。active方法会将参数封装成为一个ResourceWeakReference，然后方到map中，如果对应key之前有值，那么调用之前值的rest方法进行清除。deactive方法现在map中移除，然后调用resource的reset方法进行清除。

值得注意的是，这里的构造方法调用了super(referent, queue)。这样如果referent被将要被GC，就会被放入到queue中。而ActiveResources#cleanReferenceQueue()方法会一直尝试从queue中获取将要被GC的resource，然后调用其cleanupActiveResource方法。该方法除了在此时被调用外，还在ActiveResources#get(key)方法中可能会获取到resource为null而被调用。

## 4.5 缓存加载、存放过程

默认的磁盘缓存策略是DiskCacheStrategy.AUTOMATIC。下面以网络图片URL以及本地图片File这两种常用的讲解缓存的加载、存放过程。

首先整个加载过程体现在Engine#load方法中：

```java
Engine.java
  public synchronized <R> LoadStatus load(
      GlideContext glideContext,
      Object model,
      Key signature,
      int width,
      int height,
      Class<?> resourceClass,
      Class<R> transcodeClass,
      Priority priority,
      DiskCacheStrategy diskCacheStrategy,
      Map<Class<?>, Transformation<?>> transformations,
      boolean isTransformationRequired,
      boolean isScaleOnlyOrNoTransform,
      Options options,
      boolean isMemoryCacheable,
      boolean useUnlimitedSourceExecutorPool,
      boolean useAnimationPool,
      boolean onlyRetrieveFromCache,
      ResourceCallback cb,
      Executor callbackExecutor) {
    long startTime = VERBOSE_IS_LOGGABLE ? LogTime.getLogTime() : 0;

    EngineKey key = keyFactory.buildKey(model, signature, width, height, transformations,
        resourceClass, transcodeClass, options);

    EngineResource<?> active = loadFromActiveResources(key, isMemoryCacheable);
    if (active != null) {
      cb.onResourceReady(active, DataSource.MEMORY_CACHE);
      if (VERBOSE_IS_LOGGABLE) {
        logWithTimeAndKey("Loaded resource from active resources", startTime, key);
      }
      return null;
    }

    EngineResource<?> cached = loadFromCache(key, isMemoryCacheable);
    if (cached != null) {
      cb.onResourceReady(cached, DataSource.MEMORY_CACHE);
      if (VERBOSE_IS_LOGGABLE) {
        logWithTimeAndKey("Loaded resource from cache", startTime, key);
      }
      return null;
    }

    EngineJob<?> current = jobs.get(key, onlyRetrieveFromCache);
    if (current != null) {
      current.addCallback(cb, callbackExecutor);
      if (VERBOSE_IS_LOGGABLE) {
        logWithTimeAndKey("Added to existing load", startTime, key);
      }
      return new LoadStatus(cb, current);
    }

    EngineJob<R> engineJob =
        engineJobFactory.build(
            key,
            isMemoryCacheable,
            useUnlimitedSourceExecutorPool,
            useAnimationPool,
            onlyRetrieveFromCache);

    DecodeJob<R> decodeJob =
        decodeJobFactory.build(
            glideContext,
            model,
            key,
            signature,
            width,
            height,
            resourceClass,
            transcodeClass,
            priority,
            diskCacheStrategy,
            transformations,
            isTransformationRequired,
            isScaleOnlyOrNoTransform,
            onlyRetrieveFromCache,
            options,
            engineJob);

    jobs.put(key, engineJob);

    engineJob.addCallback(cb, callbackExecutor);
    engineJob.start(decodeJob);

    if (VERBOSE_IS_LOGGABLE) {
      logWithTimeAndKey("Started new load", startTime, key);
    }
    return new LoadStatus(cb, engineJob);
  }
```

从注释和代码中知道缓存首先是检查active resource、然后检查memory cache，最后是交给了job。那么毫无疑问job会进行disk cache的读操作。

只要是缓存，就离不开Key，所以先看看从active resource和memory cache中取缓存的Key-EngineKey的结构。

EngineKey的组成

| 组成            | 注释                                                         |
| --------------- | ------------------------------------------------------------ |
| model           | 三步加载流程load的入参，该数据类型转换成了Object类型。       |
| signature       | BaseRequestOptions`的成员变量，默认会是`EmptySignature.obtain()` 在加载本地resource资源时会变成`ApplicationVersionSignature.obtain(context) |
| width height    | 如果没有指定`override(int size)`，那么将得到view的size       |
| transformations | 默认会基于`ImageView`的scaleType设置对应的四个`Transformation`；<br/>如果指定了`transform`，那么就基于该值进行设置；<br/>详见`BaseRequestOptions.transform(Transformation, boolean)` |
| resourceClass   | 解码后的资源，如果没有`asBitmap`、`asGif`，一般会是`Object`  |
| transcodeClass  | 最终要转换成的数据类型，根据`as`方法确定，加载本地res或者网络URL，都会调用`asDrawable`，所以为`Drawable` |
| options         | 如果没有设置过`transform`，此处会根据ImageView的scaleType默认指定一个KV，详见上一文2.2节 |

显然，在多次加载同一个model的过程中，即使有少许改动（比如View宽高），Glide都不会认为这是同一个Key。

回到Engine#load方法中，active resource和memory cache状态的资源都是DataSource.MEMORY_CACHE状态，从缓存加载成功后的回调可以看出。加载出的资源都是EngineResource。

```java
class EngineResource<Z> implements Resource<Z> {
  private final boolean isCacheable;
  private final boolean isRecyclable;
  private final Resource<Z> resource;

  private ResourceListener listener;
  private Key key;
  private int acquired;
  private boolean isRecycled;

  interface ResourceListener {
    void onResourceReleased(Key key, EngineResource<?> resource);
  }

  EngineResource(Resource<Z> toWrap, boolean isCacheable, boolean isRecyclable) {
    resource = Preconditions.checkNotNull(toWrap);
    this.isCacheable = isCacheable;
    this.isRecyclable = isRecyclable;
  }

  synchronized void setResourceListener(Key key, ResourceListener listener) {
    this.key = key;
    this.listener = listener;
  }

  Resource<Z> getResource() {
    return resource;
  }

  boolean isCacheable() {
    return isCacheable;
  }

  @NonNull
  @Override
  public Class<Z> getResourceClass() {
    return resource.getResourceClass();
  }

  @NonNull
  @Override
  public Z get() {
    return resource.get();
  }

  @Override
  public int getSize() {
    return resource.getSize();
  }

  @Override
  public synchronized void recycle() {
    if (acquired > 0) {
      throw new IllegalStateException("Cannot recycle a resource while it is still acquired");
    }
    if (isRecycled) {
      throw new IllegalStateException("Cannot recycle a resource that has already been recycled");
    }
    isRecycled = true;
    if (isRecyclable) {
      resource.recycle();
    }
  }

  /**
   * Increments the number of consumers using the wrapped resource. Must be called on the main
   * thread.
   *
   * <p>This must be called with a number corresponding to the number of new consumers each time new
   * consumers begin using the wrapped resource. It is always safer to call acquire more often than
   * necessary. Generally external users should never call this method, the framework will take care
   * of this for you.
   */
  synchronized void acquire() {
    if (isRecycled) {
      throw new IllegalStateException("Cannot acquire a recycled resource");
    }
    ++acquired;
  }

  /**
   * Decrements the number of consumers using the wrapped resource. Must be called on the main
   * thread.
   *
   * <p>This must only be called when a consumer that called the {@link #acquire()} method is now
   * done with the resource. Generally external users should never call this method, the framework
   * will take care of this for you.
   */
  // listener is effectively final.
  @SuppressWarnings("SynchronizeOnNonFinalField")
  void release() {
    // To avoid deadlock, always acquire the listener lock before our lock so that the locking
    // scheme is consistent (Engine -> EngineResource). Violating this order leads to deadlock
    // (b/123646037).
    synchronized (listener) {
      synchronized (this) {
        if (acquired <= 0) {
          throw new IllegalStateException("Cannot release a recycled or not yet acquired resource");
        }
        if (--acquired == 0) {
          listener.onResourceReleased(key, this);
        }
      }
    }
  }

  ...
}
```

在release方法中，如果引用计数acquired为0， 那么会调用listener#onResourceReleased(key, this)方法，通知外界此资源已经释放了。实际上所有的listener都是Engine对象，在Engine#onResourceRelease方法中会将此资源方法memory cache中。

```java
Engine.java
  @Override
  public synchronized void onResourceReleased(Key cacheKey, EngineResource<?> resource) {
    activeResources.deactivate(cacheKey);
    if (resource.isCacheable()) {
      cache.put(cacheKey, resource);
    } else {
      resourceRecycler.recycle(resource);
    }
  }
```

了解了EngineResource之后，再回到Engine#load方法。首先从active resource和memory cache中检查缓存。

```java
Engine.java
  @Nullable
  private EngineResource<?> loadFromActiveResources(Key key, boolean isMemoryCacheable) {
    if (!isMemoryCacheable) { // ⚠️
      return null;
    }
    EngineResource<?> active = activeResources.get(key);
    if (active != null) {
      active.acquire();
    }

    return active;
  }

  private EngineResource<?> loadFromCache(Key key, boolean isMemoryCacheable) {
    if (!isMemoryCacheable) { // ⚠️
      return null;
    }

    EngineResource<?> cached = getEngineResourceFromCache(key);
    if (cached != null) {
      cached.acquire();
      // 将从memory cache获取的资源重新放入到alive resource集合中。
      activeResources.activate(key, cached);
    }
    return cached;
  }

  private EngineResource<?> getEngineResourceFromCache(Key key) {
    Resource<?> cached = cache.remove(key);

    final EngineResource<?> result;
    if (cached == null) {
      result = null;
    } else if (cached instanceof EngineResource) {
      // Save an object allocation if we've cached an EngineResource (the typical case).
      result = (EngineResource<?>) cached;
    } else {
      result = new EngineResource<>(cached, true /*isMemoryCacheable*/, true /*isRecyclable*/);
    }
    return result;
  }
```

首先判断skipMemoryCache(true)是否进行了设置。如果设置过，那么这两个方法返回null。否则会从内存缓存中进行检查。只要找到了EngineResource资源，该资源的引用计数就加一。如果是memory cache的资源，那么此资源重新放入到alive resource集合中。第一次运行是没有内存缓存的。现在就来到DecodeJob和EngineJob这里。DecodeJob实现了Runnable接口，然后被EngineJob#start方法提交到对应的线程池中去执行。

EngineJob是一个通过添加、移除回调来管理加载操作的类。EngineJob持有DecodJob对象。

DecodeJob#run()方法真正实现是调用了runWrapped方法。

```java
DecodeJob
  private void runWrapped() {
    switch (runReason) {
      case INITIALIZE:
        stage = getNextStage(Stage.INITIALIZE);
        currentGenerator = getNextGenerator();
        runGenerators();
        break;
      case SWITCH_TO_SOURCE_SERVICE:
        runGenerators();
        break;
      case DECODE_DATA:
        decodeFromRetrievedData();
        break;
      default:
        throw new IllegalStateException("Unrecognized run reason: " + runReason);
    }
  }

  private Stage getNextStage(Stage current) {
    switch (current) {
      case INITIALIZE:
        return diskCacheStrategy.decodeCachedResource()
            ? Stage.RESOURCE_CACHE : getNextStage(Stage.RESOURCE_CACHE);
      case RESOURCE_CACHE:
        return diskCacheStrategy.decodeCachedData()
            ? Stage.DATA_CACHE : getNextStage(Stage.DATA_CACHE);
      case DATA_CACHE:
        // Skip loading from source if the user opted to only retrieve the resource from cache.
        return onlyRetrieveFromCache ? Stage.FINISHED : Stage.SOURCE;
      case SOURCE:
      case FINISHED:
        return Stage.FINISHED;
      default:
        throw new IllegalArgumentException("Unrecognized stage: " + current);
    }
  }

  private DataFetcherGenerator getNextGenerator() {
    switch (stage) {
      case RESOURCE_CACHE:
        return new ResourceCacheGenerator(decodeHelper, this);
      case DATA_CACHE:
        return new DataCacheGenerator(decodeHelper, this);
      case SOURCE:
        return new SourceGenerator(decodeHelper, this);
      case FINISHED:
        return null;
      default:
        throw new IllegalStateException("Unrecognized stage: " + stage);
    }
  }

  private void runGenerators() {
    currentThread = Thread.currentThread();
    startFetchTime = LogTime.getLogTime();
    boolean isStarted = false;
    while (!isCancelled && currentGenerator != null
        && !(isStarted = currentGenerator.startNext())) {
      stage = getNextStage(stage);
      currentGenerator = getNextGenerator();

      if (stage == Stage.SOURCE) {
        reschedule();
        return;
      }
    }
    // We've run out of stages and generators, give up.
    if ((stage == Stage.FINISHED || isCancelled) && !isStarted) {
      notifyFailed();
    }

    // Otherwise a generator started a new load and we expect to be called back in
    // onDataFetcherReady.
  }
```

由于runReason初始为RunReason.INITIALIZE，又diskCacheStrategy为默认的DiskCacheStrategy.AUTOMATIC，且没有设置onlayRetriveFromCache(true)。所以，decode data的状态依次是INITIALIZE` -> `RESOURCE_CACHE` -> `DATA_CACHE` -> `SOURCE` -> `FINISHED。对应的DataFetcherGenerator依次是ResourceCacheGenerator -> DataCacheGenerator -> SourceGenerator。

ResourceCacheGenerator负责在磁盘缓存查找resource，DataCacheGenerator负责在磁盘缓存中查找data资源。当两个cache generator都找不到时，会交给SourceGenerator从source中进行加载。对于一个网络图片来说，就是加载网络图片；对于本地资源来说，就是加载本地图片。

先看看ResoureCacheGenerator中查找缓存时key的结构：

```java
      currentKey =
          new ResourceCacheKey(// NOPMD AvoidInstantiatingObjectsInLoops
              helper.getArrayPool(),
              sourceId,
              helper.getSignature(),
              helper.getWidth(),
              helper.getHeight(),
              transformation,
              resourceClass,
              helper.getOptions());
```

ResourceCacheGenerator中的key ResourceCacheKey的结构

| 组成                 | 注释                                                         |
| -------------------- | ------------------------------------------------------------ |
| arrayPool            | `GlideBuilder.build`时初始化，默认为`LruArrayPool`；*但不参与key的`equals`方法* |
| sourceKey            | 如果请求的是URL，那么此处会是一个`GlideUrl`                  |
| signature            | BaseRequestOptions`的成员变量，默认会是`EmptySignature.obtain()` 在加载本地resource资源时会变成`ApplicationVersionSignature.obtain(context) |
| width height         | 如果没有指定`override(int size)`，那么将得到view的size       |
| transformation       | 默认会根据`ImageView`的scaleType设置对应的`BitmapTransformation`；<br/>如果指定了`transform`，那么就会是指定的值 |
| decodedResourceClass | 可以被编码成的资源类型，比如`BitmapDrawable`等               |
| options              | 如果没有设置过`transform`，此处会根据ImageView的scaleType默认指定一个KV， |

接着看下DataCacheGenerator中key的组成。

```java
Key originalKey = new DataCacheKey(sourceId, helper.getSignature());
```

DataCacheKey的结构

| 组成      | 注释                                                         |
| --------- | ------------------------------------------------------------ |
| sourceKey | 如果请求的是URL，那么此处会是一个`GlideUrl`                  |
| signature | BaseRequestOptions`的成员变量，默认会是`EmptySignature.obtain()` 在加载本地resource资源时会变成`ApplicationVersionSignature.obtain(context) |

### 4.5.1 缓存键(Cache Keys)

参考文章：

https://muyangmin.github.io/glide-docs-cn/doc/caching.html

https://bumptech.github.io/glide/doc/caching.html#cache-keys

在 Glide v4 里，所有缓存键都包含至少两个元素：

1. 请求加载的 model（File, Uri, Url）。如果你使用自定义的 model, 它需要正确地实现 `hashCode()` 和 `equals()`
2. 一个可选的 [`签名`(Signature)](https://muyangmin.github.io/glide-docs-cn/javadocs/400/com/bumptech/glide/request/RequestOptions.html#signature-com.bumptech.glide.load.Key-)

另外，步骤1-3(活动资源，内存缓存，资源磁盘缓存)的缓存键还包含一些其他数据，包括：

1. 宽度和高度
2. 可选的`变换（Transformation）`
3. 额外添加的任何 [`选项(Options)`](https://muyangmin.github.io/glide-docs-cn/javadocs/400/com/bumptech/glide/load/Option.html)
4. 请求的数据类型 (Bitmap, GIF, 或其他)

活动资源和内存缓存使用的键还和磁盘资源缓存略有不同，以适应内存 [`选项(Options)`](https://muyangmin.github.io/glide-docs-cn/javadocs/400/com/bumptech/glide/load/Option.html)，比如影响 Bitmap 配置的选项或其他解码时才会用到的参数。

为了生成磁盘缓存上的缓存键名称，以上的每个元素会被哈希化以创建一个单独的字符串键名，并在随后作为磁盘缓存上的文件名使用。

### 4.5.2 ResourceCacheGenerator中查找resoure cache

根据ResourceCacheKey查找缓存，代码在DataLruCacheWrapper#get方法中。

```java
DataLruCacheWrapper.java
  public File get(Key key) {
    String safeKey = safeKeyGenerator.getSafeKey(key);
    if (Log.isLoggable(TAG, Log.VERBOSE)) {
      Log.v(TAG, "Get: Obtained: " + safeKey + " for for Key: " + key);
    }
    File result = null;
    try {
      // It is possible that the there will be a put in between these two gets. If so that shouldn't
      // be a problem because we will always put the same value at the same key so our input streams
      // will still represent the same data.
      final DiskLruCache.Value value = getDiskCache().get(safeKey);
      if (value != null) {
        result = value.getFile(0);
      }
    } catch (IOException e) {
      if (Log.isLoggable(TAG, Log.WARN)) {
        Log.w(TAG, "Unable to get from disk cache", e);
      }
    }
    return result;
  }
```

这里先使用SafeKeyGenerator生成一个String类型的safeKey，实际上就是对ResoureCacheKey中每个字段都使用SHA-256加密，然后将得到的字节数组转换成16进制的字符串。

然后在Disk Cache中根据safeKey查找，查找的结果以Value实体返回来，从Value中获取对应的文件。

```java
DiskLruCache.java
  public synchronized Value get(String key) throws IOException {
    checkNotClosed();
    Entry entry = lruEntries.get(key);
    if (entry == null) {
      return null;
    }

    if (!entry.readable) {
      return null;
    }

    for (File file : entry.cleanFiles) {
        // A file must have been deleted manually!
        if (!file.exists()) {
            return null;
        }
    }

    redundantOpCount++;
    journalWriter.append(READ);
    journalWriter.append(' ');
    journalWriter.append(key);
    journalWriter.append('\n');
    if (journalRebuildRequired()) {
      executorService.submit(cleanupCallable);
    }

    return new Value(key, entry.sequenceNumber, entry.cleanFiles, entry.lengths);
  }
```

回到ResourceCacheGenerator中，如果确实有缓存，那么会加载该缓存文件。对于URL来说，调用了ByteBufferFetcher进行缓存文件的加载，加载成功返回一个ByteBuffer，并调用callback也就是ResourcCacheGenerator#onDataReady方法。然后ResoureCacheGenerator又回调DecodeJob#onDataFetcherReady进行后续的解码操作。

```java
ResourceCacheGenerator.java
  @Override
  public void onDataReady(Object data) {
    cb.onDataFetcherReady(sourceKey, data, loadData.fetcher, DataSource.RESOURCE_DISK_CACHE,
        currentKey);
  }
```

如果ResourceCacheGenerator没有找到磁盘资源缓存，那么就会交给DataCacheGenerator。该类大体流程和ResourceCacheGenerator一样。有点不同的是，DataCacheGenerator的构造器有两个，其中`DataCacheGenerator(List<Key>, DecodeHelper<?>, FetcherReadyCallback)`构造器是给`SourceGenerator`准备的。因为如果开没有磁盘数据缓存，那么从源头加载后，肯定需要进行磁盘缓存操作。所以，SourceGenerator会将加载后的数据保存到磁盘中，然后转交给DataCacheGenerator从磁盘中读取出来。

```java
DataCacheGenerator.java
  @Override
  public void onDataReady(Object data) {
    cb.onDataFetcherReady(sourceKey, data, loadData.fetcher, DataSource.DATA_DISK_CACHE, sourceKey);
  }

```

如果DataCacheGenerator没有取到缓存，那么交给SourceGenerator从源头加载。加载成功后回调SourceGenerator#onDataReady()方法。

```java
SourceGenerator.java
  @Override
  public void onDataReady(Object data) {
    DiskCacheStrategy diskCacheStrategy = helper.getDiskCacheStrategy();
    if (data != null && diskCacheStrategy.isDataCacheable(loadData.fetcher.getDataSource())) {
      dataToCache = data;
      // We might be being called back on someone else's thread. Before doing anything, we should
      // reschedule to get back onto Glide's thread.
      cb.reschedule();
    } else {
      cb.onDataFetcherReady(loadData.sourceKey, data, loadData.fetcher,
          loadData.fetcher.getDataSource(), originalKey);
    }
  }
```

这里先判断数据是否能够磁盘缓存；如果能，则经过EngineJob、DecodeJob的调度，重新调用SourceGenerator#startNext方法，进行磁盘缓存的写入，并转交给DataCacheGenerator完成缓存读取。如果不能够磁盘缓存就通知DecodeJob已经加载成功了。

默认的磁盘缓存策略是DiskCacheStrategy.AUTOMATIC，其data只缓存DataSource.REMOTE的数据，也就是URL这种。

在加载就是URL的情况下，看下磁盘缓存写入过程。

首先经过EngineJob、DecodeJob的调度之后，run方法又执行了。SourceGenerator#startNext()方法调用。由于dataToCache保存了获取的原始数据，所以会调用cacheData方法进行缓存的写入操作。cacheData方法先构建一个DataCacheKey将Data写入磁盘，然后new一个DataCacheGenerator。回到SourceGenerator#startNext方法，sourceCacheGenerator不为null，就会调用DataCacheGenerator#startNext()方法从DiskCache中获取缓存并加载。

```java
SourceGenerator.java
  public boolean startNext() {
    if (dataToCache != null) {
      Object data = dataToCache;
      dataToCache = null;
      // 1 写入磁盘缓存。
      cacheData(data);
    }

    // sourceCacheGenerator不为null准备读取disk cache中的数据。
    if (sourceCacheGenerator != null && sourceCacheGenerator.startNext()) {
      return true;
    }
    sourceCacheGenerator = null;
	...
  }

  private void cacheData(Object dataToCache) {
    long startTime = LogTime.getLogTime();
    try {
      Encoder<Object> encoder = helper.getSourceEncoder(dataToCache);
      DataCacheWriter<Object> writer =
          new DataCacheWriter<>(encoder, dataToCache, helper.getOptions());
      // 1 构建DataCacheKey，将data写入到disk cache中。
      originalKey = new DataCacheKey(loadData.sourceKey, helper.getSignature());
      helper.getDiskCache().put(originalKey, writer);
      if (Log.isLoggable(TAG, Log.VERBOSE)) {
        Log.v(TAG, "Finished encoding source to cache"
            + ", key: " + originalKey
            + ", data: " + dataToCache
            + ", encoder: " + encoder
            + ", duration: " + LogTime.getElapsedMillis(startTime));
      }
    } finally {
      loadData.fetcher.cleanup();
    }
	// 2 构建DataCacheGenerator。
    sourceCacheGenerator =
        new DataCacheGenerator(Collections.singletonList(loadData.sourceKey), helper, this);
  }
```

这样数据磁盘缓存通过SourceGenerator写入完成，通过DataCacheGenerator读取到了数据磁盘缓存。会回调到DecodeJob#onDataFetcherReady()方法。该方法完成两个事情：

(1) 将原始数据data转变为可以共ImageView显示的Resouce数据。

(2) 显示resource数据。

```java
DecodeJob.java
  public void onDataFetcherReady(Key sourceKey, Object data, DataFetcher<?> fetcher,
      DataSource dataSource, Key attemptedKey) {
    this.currentSourceKey = sourceKey;
    this.currentData = data;
    this.currentFetcher = fetcher;
    this.currentDataSource = dataSource;
    this.currentAttemptingKey = attemptedKey;
    if (Thread.currentThread() != currentThread) {
      runReason = RunReason.DECODE_DATA;
      callback.reschedule(this);
    } else {
      GlideTrace.beginSection("DecodeJob.decodeFromRetrievedData");
      try {
        decodeFromRetrievedData();//😷😷😷
      } finally {
        GlideTrace.endSection();
      }
    }
  }

  private void decodeFromRetrievedData() {
    if (Log.isLoggable(TAG, Log.VERBOSE)) {
      logWithTimeAndKey("Retrieved data", startFetchTime,
          "data: " + currentData
              + ", cache key: " + currentSourceKey
              + ", fetcher: " + currentFetcher);
    }
    Resource<R> resource = null;
    try {
      resource = decodeFromData(currentFetcher, currentData, currentDataSource);//😷😷😷
    } catch (GlideException e) {
      e.setLoggingDetails(currentAttemptingKey, currentDataSource);
      throwables.add(e);
    }
    if (resource != null) {
      notifyEncodeAndRelease(resource, currentDataSource);
    } else {
      runGenerators();
    }
  }

  private <Data> Resource<R> decodeFromData(DataFetcher<?> fetcher, Data data,
      DataSource dataSource) throws GlideException {
    try {
      if (data == null) {
        return null;
      }
      long startTime = LogTime.getLogTime();
      Resource<R> result = decodeFromFetcher(data, dataSource); //😷😷😷
      if (Log.isLoggable(TAG, Log.VERBOSE)) {
        logWithTimeAndKey("Decoded result " + result, startTime);
      }
      return result;
    } finally {
      fetcher.cleanup();
    }
  }

  @SuppressWarnings("unchecked")
  private <Data> Resource<R> decodeFromFetcher(Data data, DataSource dataSource)
      throws GlideException {
    LoadPath<Data, ?, R> path = decodeHelper.getLoadPath((Class<Data>) data.getClass());
    return runLoadPath(data, dataSource, path);
  }

  private <Data, ResourceType> Resource<R> runLoadPath(Data data, DataSource dataSource,
      LoadPath<Data, ResourceType, R> path) throws GlideException {
    Options options = getOptionsWithHardwareConfig(dataSource);
    DataRewinder<Data> rewinder = glideContext.getRegistry().getRewinder(data);
    try {
      // ResourceType in DecodeCallback below is required for compilation to work with gradle.
      return path.load(
          rewinder, options, width, height, new DecodeCallback<ResourceType>(dataSource));
    } finally {
      rewinder.cleanup();
    }
  }
```



```java
LoadPath.java
  public Resource<Transcode> load(DataRewinder<Data> rewinder, @NonNull Options options, int width,
      int height, DecodePath.DecodeCallback<ResourceType> decodeCallback) throws GlideException {
    List<Throwable> throwables = Preconditions.checkNotNull(listPool.acquire());
    try {
      return loadWithExceptionList(rewinder, options, width, height, decodeCallback, throwables);
    } finally {
      listPool.release(throwables);
    }
  }

  private Resource<Transcode> loadWithExceptionList(DataRewinder<Data> rewinder,
      @NonNull Options options,
      int width, int height, DecodePath.DecodeCallback<ResourceType> decodeCallback,
      List<Throwable> exceptions) throws GlideException {
    Resource<Transcode> result = null;
    //noinspection ForLoopReplaceableByForEach to improve perf
    for (int i = 0, size = decodePaths.size(); i < size; i++) {
      DecodePath<Data, ResourceType, Transcode> path = decodePaths.get(i);
      try {
        result = path.decode(rewinder, width, height, options, decodeCallback); // //😷😷😷
      } catch (GlideException e) {
        exceptions.add(e);
      }
      if (result != null) {
        break;
      }
    }

    if (result == null) {
      throw new GlideException(failureMessage, new ArrayList<>(exceptions));
    }

    return result;
  }
```



```java
DecodePath.java
  public Resource<Transcode> decode(DataRewinder<DataType> rewinder, int width, int height,
      @NonNull Options options, DecodeCallback<ResourceType> callback) throws GlideException {
    Resource<ResourceType> decoded = decodeResource(rewinder, width, height, options);
    Resource<ResourceType> transformed = callback.onResourceDecoded(decoded);//😷😷😷
    return transcoder.transcode(transformed, options);
  }
```

重新回调到DecodJob#DecodJobCallback#onResourceDecoded()方法。

```java
DecodeJob.java
  private void notifyComplete(Resource<R> resource, DataSource dataSource) {
    setNotifiedOrThrow();
    callback.onResourceReady(resource, dataSource); //😷😷😷回调到EngineJob#onResoureReady()方法
  }
    
  private void notifyEncodeAndRelease(Resource<R> resource, DataSource dataSource) {
    if (resource instanceof Initializable) {
      ((Initializable) resource).initialize();
    }

    Resource<R> result = resource;
    LockedResource<R> lockedResource = null;
    if (deferredEncodeManager.hasResourceToEncode()) {
      lockedResource = LockedResource.obtain(resource);
      result = lockedResource;
    }
	//😷😷😷
    notifyComplete(result, dataSource);

    stage = Stage.ENCODE;
    try {
      if (deferredEncodeManager.hasResourceToEncode()) {
        deferredEncodeManager.encode(diskCacheProvider, options);
      }
    } finally {
      if (lockedResource != null) {
        lockedResource.unlock();
      }
    }
    // Call onEncodeComplete outside the finally block so that it's not called if the encode process
    // throws.
    onEncodeComplete();
  }


  @Synthetic
  @NonNull
  <Z> Resource<Z> onResourceDecoded(DataSource dataSource,
      @NonNull Resource<Z> decoded) {
    @SuppressWarnings("unchecked")
    Class<Z> resourceSubClass = (Class<Z>) decoded.get().getClass();
    Transformation<Z> appliedTransformation = null;
    Resource<Z> transformed = decoded;
    if (dataSource != DataSource.RESOURCE_DISK_CACHE) {
      appliedTransformation = decodeHelper.getTransformation(resourceSubClass);
      transformed = appliedTransformation.transform(glideContext, decoded, width, height);
    }
    // TODO: Make this the responsibility of the Transformation.
    if (!decoded.equals(transformed)) {
      decoded.recycle();
    }

    final EncodeStrategy encodeStrategy;
    final ResourceEncoder<Z> encoder;
    if (decodeHelper.isResourceEncoderAvailable(transformed)) {
      encoder = decodeHelper.getResultEncoder(transformed);
      encodeStrategy = encoder.getEncodeStrategy(options);
    } else {
      encoder = null;
      encodeStrategy = EncodeStrategy.NONE;
    }

    Resource<Z> result = transformed;
    boolean isFromAlternateCacheKey = !decodeHelper.isSourceKey(currentSourceKey);
    if (diskCacheStrategy.isResourceCacheable(isFromAlternateCacheKey, dataSource,
        encodeStrategy)) {
      if (encoder == null) {
        throw new Registry.NoResultEncoderAvailableException(transformed.get().getClass());
      }
      final Key key;
      switch (encodeStrategy) {
        case SOURCE:
          key = new DataCacheKey(currentSourceKey, signature);
          break;
        case TRANSFORMED:
          key =
              new ResourceCacheKey(
                  decodeHelper.getArrayPool(),
                  currentSourceKey,
                  signature,
                  width,
                  height,
                  appliedTransformation,
                  resourceSubClass,
                  options);
          break;
        default:
          throw new IllegalArgumentException("Unknown strategy: " + encodeStrategy);
      }

      LockedResource<Z> lockedResult = LockedResource.obtain(transformed);
      deferredEncodeManager.init(key, encoder, lockedResult);
      result = lockedResult;
    }
    return result;
  }

  private final class DecodeCallback<Z> implements DecodePath.DecodeCallback<Z> {

    private final DataSource dataSource;

    @Synthetic
    DecodeCallback(DataSource dataSource) {
      this.dataSource = dataSource;
    }

    @NonNull
    @Override
    public Resource<Z> onResourceDecoded(@NonNull Resource<Z> decoded) {
      return DecodeJob.this.onResourceDecoded(dataSource, decoded);
    }
  }
```



```java
EngineJob.java
  @Synthetic
  synchronized void callCallbackOnResourceReady(ResourceCallback cb) {
    try {
      // This is overly broad, some Glide code is actually called here, but it's much
      // simpler to encapsulate here than to do so at the actual call point in the
      // Request implementation.
      cb.onResourceReady(engineResource, dataSource);//😷😷😷 回调到SingelRequest#onResourceReady()方法。
    } catch (Throwable t) {
      throw new CallbackException(t);
    }
  }

  public void onResourceReady(Resource<R> resource, DataSource dataSource) {
    synchronized (this) {
      this.resource = resource;
      this.dataSource = dataSource;
    }
    notifyCallbacksOfResult();//😷😷😷
  }

  @Synthetic
  void notifyCallbacksOfResult() {
    ResourceCallbacksAndExecutors copy;
    Key localKey;
    EngineResource<?> localResource;
    synchronized (this) {
      stateVerifier.throwIfRecycled();
      if (isCancelled) {
        ...
      } else if (cbs.isEmpty()) {
        throw new IllegalStateException("Received a resource without any callbacks to notify");
      } else if (hasResource) {
        throw new IllegalStateException("Already have resource");
      }
      engineResource = engineResourceFactory.build(resource, isCacheable);
      // Hold on to resource for duration of our callbacks below so we don't recycle it in the
      // middle of notifying if it synchronously released by one of the callbacks. Acquire it under
      // a lock here so that any newly added callback that executes before the next locked section
      // below can't recycle the resource before we call the callbacks.
      hasResource = true;
      copy = cbs.copy();
      incrementPendingCallbacks(copy.size() + 1);

      localKey = key;
      localResource = engineResource;
    }

    listener.onEngineJobComplete(this, localKey, localResource); //😷😷😷

    for (final ResourceCallbackAndExecutor entry : copy) {
      entry.executor.execute(new CallResourceReady(entry.cb));//😷😷😷
    }
    decrementPendingCallbacks();
  }

  private class CallResourceReady implements Runnable {

    private final ResourceCallback cb;

    CallResourceReady(ResourceCallback cb) {
      this.cb = cb;
    }

    @Override
    public void run() {
      synchronized (EngineJob.this) {
        if (cbs.contains(cb)) {
          // Acquire for this particular callback.
          engineResource.acquire();
          callCallbackOnResourceReady(cb);//😷😷😷
          removeCallback(cb);
        }
        decrementPendingCallbacks();
      }
    }
  }
```



```java
Engine.java
  @SuppressWarnings("unchecked")
  @Override
  public synchronized void onEngineJobComplete(
      EngineJob<?> engineJob, Key key, EngineResource<?> resource) {
    // A null resource indicates that the load failed, usually due to an exception.
    if (resource != null) {
      resource.setResourceListener(key, this);

      if (resource.isCacheable()) {
        activeResources.activate(key, resource);
      }
    }

    jobs.removeIfCurrent(key, engineJob);
  }
```



```java
SingleRequest.java
  @SuppressWarnings("unchecked")
  @Override
  public synchronized void onResourceReady(Resource<?> resource, DataSource dataSource) {
    ...
    Object received = resource.get();
    ...
    onResourceReady((Resource<R>) resource, (R) received, dataSource);//😷😷😷
  }

  private synchronized void onResourceReady(Resource<R> resource, R result, DataSource dataSource) {
    // We must call isFirstReadyResource before setting status.
    boolean isFirstResource = isFirstReadyResource();
    status = Status.COMPLETE;
    this.resource = resource;
    ...

    isCallingCallbacks = true;
    try {
      boolean anyListenerHandledUpdatingTarget = false;
      if (requestListeners != null) {
        for (RequestListener<R> listener : requestListeners) {
          anyListenerHandledUpdatingTarget |=
              listener.onResourceReady(result, model, target, dataSource, isFirstResource);
        }
      }
      anyListenerHandledUpdatingTarget |=
          targetListener != null
              && targetListener.onResourceReady(result, model, target, dataSource, isFirstResource);

      if (!anyListenerHandledUpdatingTarget) {
        Transition<? super R> animation =
            animationFactory.build(dataSource, isFirstResource);
        target.onResourceReady(result, animation);//😷😷😷
      }
    } finally {
      isCallingCallbacks = false;
    }

    notifyLoadSuccess();
  }
```



```java
ImageViewTarget.java
  @Override
  public void onResourceReady(@NonNull Z resource, @Nullable Transition<? super Z> transition) {
    if (transition == null || !transition.transition(resource, this)) {
      setResourceInternal(resource);//😷😷😷
    } else {
      maybeUpdateAnimatable(resource);
    }
  }

  private void setResourceInternal(@Nullable Z resource) {
    // Order matters here. Set the resource first to make sure that the Drawable has a valid and
    // non-null Callback before starting it.
    setResource(resource);
    maybeUpdateAnimatable(resource);
  }
```

### 4.5.3 调试总结

经调试发现加载网络图片的缓存有如下的结果

Glide加载网络图片的三步流程是：

```java
        val options = RequestOptions()
            .placeholder(ColorDrawable(Color.GRAY))
            .error(ColorDrawable(Color.RED))
            .fallback(ColorDrawable(Color.CYAN))
        Glide.with(this).load(URL).apply(options).into(ivGlide!!)
```

所以图片加载对应的DiskCacheStrategy是默认的DiskCacheStrategy.ATUOMATIC。

第一次加载，内存缓存是没有资源的，所以会通过DecodeJob执行图片加载任务，这个时候的stage时Stage.INITIALIZED，切换stage为Stage.MEMORY_CACHE。首先将数据查找转发给ResourceCacheGenerator，根据ResourceCacheKey在磁盘中查找，磁盘缓存是没有的。stage切换为Stage.DATA_CACHE然后将数据查找转发给DataCacheGenerator，根据DataCacheKey在磁盘中查找，也是没有的。切换stage为Stage.Source，将数据查找转发给了SoureGenerator。SourceGenerator做网络请求，获取到了数据后，stage切换为Stage.FINISHED，SourceGenerator负责将数据以data disk cache的形式保存到磁盘缓存。然后构造DataCacheGenerator从磁盘中将data disk cache读取出来，并将数据交给DecodeJob去解码，解码后得到BitmapResource类型的数据，然后经过transform。transform会根据设置的图片大小和ImageView的scaleType进行裁切，将stage切换为Stage.ENCODE。由于DataSource不是LOCAL，不会对解码、转换之后的resource以resource disk cache的形式保存到磁盘中。DecodJob将解码、转换之后的BitmapResource传给EngineJob，EngineJob将resource转换成EngineResource，并将资源以alive resource的形式保存到内存缓存中。EngineJob将DataSource.DATA_DISK_CACHE的EngineResource传给SingleRequest，SingleRequest获取对应的Drawable传给Target。Target持有的ImageView拿到drawable将从placeholder切换为drawable。

# 5 RequestBuilder中高级点的API以及Target

## 5.1 Target

Glide.with(xx).load(xx).into(xx)。在into(ImageView)过程中，会将ImageView包装成ViewTarget类。如果调用过asBitmap()方法，那么此处会是BitmapImageViewTarget，否则是DrawableImageViewTarget。BitmapImageViewTarget和DrawableImageViewTarget除了setResource方法中调用的设置图片的api不同之外，没有任何区别。

```java
public class ImageViewTargetFactory {
  @NonNull
  @SuppressWarnings("unchecked")
  public <Z> ViewTarget<ImageView, Z> buildTarget(@NonNull ImageView view,
      @NonNull Class<Z> clazz) {
    if (Bitmap.class.equals(clazz)) {
      return (ViewTarget<ImageView, Z>) new BitmapImageViewTarget(view);
    } else if (Drawable.class.isAssignableFrom(clazz)) {
      return (ViewTarget<ImageView, Z>) new DrawableImageViewTarget(view);
    } else {
      throw new IllegalArgumentException(
          "Unhandled class: " + clazz + ", try .as*(Class).transcode(ResourceTranscoder)");
    }
  }
}
```

DrawableImageViewTarget的继承链如下：DrawableImageViewTarget -> ImageViewTarget -> ViewTarget -> BaseTarget -> Target。

.Target是一个继承了LifecycleListener的接口，该接口定义了资源加载过程中的回调方法。

典型的资源加载周期为onLoadStarted -> onResourceReady/onLoadFailed -> onLoadCleared。

.BaseTarget是一个实现Target接口的抽象类。该类实现了setRequest/getRequest两个方法。其他方法相当于适配器模式的实现。

.ViewTarget是继承BaseTarget的抽象类，重写了setRequest/getRequest方法，这两个方法会调用View.setTag/View.getTag将Request对象传入。

.ImageViewTarget是继承ViewTarget的抽象类。该类的作用是在图片加载的周期方法中给ImageView设置对应的资源。但由于加载成功后返回的资源可能是Bitmap或者Drawabel，所以这个不确定类型的加载又serResource抽象方法生命。待子类BitmapImageViewTarget和DrawableImageViewTarget实现。

.DrawableImageViewTarget是继承ImageViewTarget的类，唯一的作用是实现了setResource方法。

## 5.2 RequestBuilder的高级api

### 5.2.1 preload

```java
  /**
   * Preloads the resource into the cache using the given width and height.
   *
   * <p> Pre-loading is useful for making sure that resources you are going to to want in the near
   * future are available quickly. </p>
   *
   * @param width  The desired width in pixels, or {@link Target#SIZE_ORIGINAL}. This will be
   *               overridden by
   *               {@link com.bumptech.glide.request.RequestOptions#override(int, int)} if
   *               previously called.
   * @param height The desired height in pixels, or {@link Target#SIZE_ORIGINAL}. This will be
   *               overridden by
   *               {@link com.bumptech.glide.request.RequestOptions#override(int, int)}} if
   *               previously called).
   * @return A {@link Target} that can be used to cancel the load via
   * {@link RequestManager#clear(Target)}.
   * @see com.bumptech.glide.ListPreloader
   */
  @NonNull
  public Target<TranscodeType> preload(int width, int height) {
    final PreloadTarget<TranscodeType> target = PreloadTarget.obtain(requestManager, width, height);
    return into(target);
  }

  /**
   * Preloads the resource into the cache using {@link Target#SIZE_ORIGINAL} as the target width and
   * height. Equivalent to calling {@link #preload(int, int)} with {@link Target#SIZE_ORIGINAL} as
   * the width and height.
   *
   * @return A {@link Target} that can be used to cancel the load via
   * {@link RequestManager#clear(Target)}
   * @see #preload(int, int)
   */
  @NonNull
  public Target<TranscodeType> preload() {
    return preload(Target.SIZE_ORIGINAL, Target.SIZE_ORIGINAL);
  }

```

在preload实现中关键点就在于PreloadTarget类。该类实现非常简单，就是在onResourceReady回调发生后，经过handler中转，最后由构造参数之一的RequestManager对象clear掉。

```java
public final class PreloadTarget<Z> extends SimpleTarget<Z> {
  private static final int MESSAGE_CLEAR = 1;
  private static final Handler HANDLER = new Handler(Looper.getMainLooper(), new Callback() {
    @Override
    public boolean handleMessage(Message message) {
      if (message.what == MESSAGE_CLEAR) {
        ((PreloadTarget<?>) message.obj).clear();
        return true;
      }
      return false;
    }
  });

  private final RequestManager requestManager;

  /**
   * Returns a PreloadTarget.
   *
   * @param width  The width in pixels of the desired resource.
   * @param height The height in pixels of the desired resource.
   * @param <Z>    The type of the desired resource.
   */
  public static <Z> PreloadTarget<Z> obtain(RequestManager requestManager, int width, int height) {
    return new PreloadTarget<>(requestManager, width, height);
  }

  private PreloadTarget(RequestManager requestManager, int width, int height) {
    super(width, height);
    this.requestManager = requestManager;
  }

  @Override
  public void onResourceReady(@NonNull Z resource, @Nullable Transition<? super Z> transition) {
    HANDLER.obtainMessage(MESSAGE_CLEAR, this).sendToTarget();
  }

  @SuppressWarnings("WeakerAccess")
  @Synthetic void clear() {
    requestManager.clear(this);
  }
}
```

### 5.2.2 submit

```java
  /**
   * Returns a future that can be used to do a blocking get on a background thread.
   *
   * <p>This method defaults to {@link Target#SIZE_ORIGINAL} for the width and the height. However,
   * since the width and height will be overridden by values passed to {@link
   * RequestOptions#override(int, int)}, this method can be used whenever {@link RequestOptions}
   * with override values are applied, or whenever you want to retrieve the image in its original
   * size.
   *
   * @see #submit(int, int)
   * @see #into(Target)
   */
  @NonNull
  public FutureTarget<TranscodeType> submit() {
    return submit(Target.SIZE_ORIGINAL, Target.SIZE_ORIGINAL);
  }

  /**
   * Returns a future that can be used to do a blocking get on a background thread.
   *
   * @param width  The desired width in pixels, or {@link Target#SIZE_ORIGINAL}. This will be
   *               overridden by
   *               {@link com.bumptech.glide.request.RequestOptions#override(int, int)} if
   *               previously called.
   * @param height The desired height in pixels, or {@link Target#SIZE_ORIGINAL}. This will be
   *               overridden by
   *               {@link com.bumptech.glide.request.RequestOptions#override(int, int)}} if
   *               previously called).
   */
  @NonNull
  public FutureTarget<TranscodeType> submit(int width, int height) {
    final RequestFutureTarget<TranscodeType> target = new RequestFutureTarget<>(width, height);
    return into(target, target, Executors.directExecutor());
  }
```

submit方法中生成一个RequestFutureTask对象，而其getSize的实现调用了cb.onSizeReady(width, height)会将width、height传递给SinigleRequest的onSizeReady方法，所以此处构造参数的width、height会覆盖掉RequestOptions的overrideWidth和overrideHeight值。RequestFutrueTarget#get方法在资源加载成功之后立即获得资源，在获取之前会阻塞当前线程，所以get方法需要在后台线程中执行。

# 7 Glide利用AppGlideModule、LibraryGlideModule更改默认配置、扩展Glide功能；GlideApp与Glide的区别在哪里？

主角是AppGlideModule，全文围绕它的两个方法：

(1) 负责改变Glide默认配置（比如磁盘、内存缓存的大小和位置的等）的applyOptions方法。

(2) 负责扩展Glide功能的registerComponents方法。

Glide在编译时，一旦发现一个自定义的AppGlideModule，就会生产多个class：GeneratedAppGlideModuleImpl、GeneratedRequestManagerFactory、GlideRequests、GlideRequest、GlideOptions。

其中GlideRequests继承于RequestManager、GlideRequest继承于RequestBuilder、GlideOptions继承于RequestOptions。

## 7.1 @GlideExtension

@GlideExtension注解修饰的类可以扩展Glide的API。该类必须使工具类。

Application和Library可以定义多个@GlideExtension注解类。Glide在编译的时候会将多个GlideExtension注解类中定义的方法合并到单个的API文件，比如GlideRequest、GlideOptions中。

GlideExtension注解类可以定义两种扩展方法：

(1) @GlideOption：为BaseRequestOptions添加自定义配置，扩展BaseRequestOptions的静态方法。

(2) @GlideType：为新的资源类型添加支持，扩展RequestManager的静态方法。



```k
MyAppExtension.kt
@GlideExtension
object MyAppExtension {
    private const val MINI_THUMB_SIZE = 100
    private val DECODE_TYPE_GIF = RequestOptions.decodeTypeOf(GifDrawable::class.java).lock()

    @GlideOption
    @JvmStatic
    fun miniThumb(options: BaseRequestOptions<*>, size: Int): BaseRequestOptions<*> {
        return options
            .fitCenter()
            .override(size)
    }

    @GlideType(GifDrawable::class)
    @JvmStatic
    fun asGifTest(requestBuilder: RequestBuilder<GifDrawable>): RequestBuilder<GifDrawable> {
        return requestBuilder
            .transition(DrawableTransitionOptions())
            .apply(DECODE_TYPE_GIF)
    }
}
```

这里为BaseRequestOptions扩展了miniThumb方法，为RequestManager扩展了asGifTest方法。所以在使用的时候可以这样：

```k
GlideApp.with(this)
    .asGifTest()
    .load(URL)
    .miniThumb()
    .into(ivGlide1)
```

这里使用的不再使Glide，而是GlideApp。GlideApp使专门用来处理这种扩展API的。

GlideRequests继承于RequestManager，里面包含了@GlideType注解修饰的API:

```java
GlideRequests.java
@SuppressWarnings("deprecation")
public class GlideRequests extends RequestManager {
  public GlideRequests(@NonNull Glide glide, @NonNull Lifecycle lifecycle,
      @NonNull RequestManagerTreeNode treeNode, @NonNull Context context) {
    super(glide, lifecycle, treeNode, context);
  }

  ...

  /**
   * @see MyAppExtension#asGifTest(RequestBuilder)
   */
  @NonNull
  @CheckResult
  public GlideRequest<GifDrawable> asGifTest() {
    return (GlideRequest<GifDrawable>) MyAppExtension.asGifTest(this.as(GifDrawable.class));
  }
    ...
}
```

GlideRequest继承于RequestBuilder，包含了@GlideOptions注解修饰的API：

```java
public class GlideRequest<TranscodeType> extends RequestBuilder<TranscodeType> implements Cloneable {
    ...
/**
   * @see MyAppExtension#miniThumb(BaseRequestOptions, int)
   */
  @SuppressWarnings("unchecked")
  @CheckResult
  @NonNull
  public GlideRequest<TranscodeType> miniThumb(int size) {
    return (GlideRequest<TranscodeType>) MyAppExtension.miniThumb(this, size);
  }

  /**
   * @see ProgressExtension#progress(BaseRequestOptions, Context)
   */
  @SuppressWarnings("unchecked")
  @CheckResult
  @NonNull
  public GlideRequest<TranscodeType> progress(@NotNull Context context) {
    return (GlideRequest<TranscodeType>) ProgressExtension.progress(this, context);
  }
}
```

GlideOptions继承于RequestOptions，里面包含了@GlideOptions修饰的API：

```java
@SuppressWarnings("deprecation")
public final class GlideOptions extends RequestOptions implements Cloneable {
/**
   * @see MyAppExtension#miniThumb(BaseRequestOptions, int)
   */
  @CheckResult
  public static GlideOptions miniThumbOf(int size) {
    return new GlideOptions().miniThumb(size);
  }

  /**
   * @see ProgressExtension#progress(BaseRequestOptions, Context)
   */
  @CheckResult
  public static GlideOptions progressOf(@NotNull Context context) {
    return new GlideOptions().progress(context);
  }
}
```

# 8 利用OkHttp、自定义Drawable、自定义ViewTarget实现带进度的图片加载功能

大致思路：

(1) 使用OkHttp取代默认的HttpUrlConnection，方便监听下载进度；

(2) 自定义ResbonseBody监听下载的进度；

(3) 自定义Interceptor用来注入自定义的ResponseBody;

(4) 自定义Drawable作为下载中的placeHolder drawable，根据progress不断跟新自身内容显示下载中的进度；

(5) 自定义ViewTarget当下载开始的时候，view注册图片下载进度的监听。

## 8.1 实现

为了让自定义的Interceptor可以加入到OkHttpClient中，需要自定义AppGlideModule并exclude默认的OkHttpLibraryGlideModule，并重写registerComponents方法。

```k
@GlideModule
@Excludes(value = [MyGlideModule::class, MyLibraryGlideModule::class, OkHttpLibraryGlideModule::class])
class MyAppGlideModule : AppGlideModule() {

    override fun applyOptions(context: Context, builder: GlideBuilder) {
        super.applyOptions(context, builder)
        builder.setDiskCache(ExternalPreferredCacheDiskCacheFactory(context))
    }

    override fun registerComponents(context: Context, glide: Glide, registry: Registry) {
    // 添加自定义的Interceptor ProgressInterceptor。
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(ProgressInterceptor())
            .build()
        registry.replace(GlideUrl::class.java, InputStream::class.java, OkHttpUrlLoader.Factory(okHttpClient))
    }

}
```



```k
class ProgressInterceptor : Interceptor {
    companion object {
        private val LISTENERS = hashMapOf<String, OnProgressChangeListener>()
        fun addListener(url: String, listener: OnProgressChangeListener) {
            LISTENERS[url] = listener
        }
        fun removeListener(url: String) {
            LISTENERS.remove(url)
        }
        fun getListener(url: String): OnProgressChangeListener? {
            return LISTENERS[url]
        }
    }
    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()
        val response = chain.proceed(request)
        val url = request.url().toString()
        val responseBody = response.body() ?: return response
        // 注入自定义的ResponseBody ProgressResponseBody。
        return response.newBuilder().body(ProgressResponseBody(responseBody, url)).build()
    }
}
```



```k
interface OnProgressChangeListener {
    fun onProgress(progress: Int)
}
```



```k
class ProgressResponseBody(private val originalResponseBody: ResponseBody, url: String)
    : ResponseBody() {

    private var listener = ProgressInterceptor.getListener(url)
    private val bufferedSource = Okio.buffer(object : ForwardingSource(originalResponseBody.source()) {
        private var totalBytesRead = 0L
        private var currentProgress = 0
        override fun read(sink: Buffer, byteCount: Long): Long {
            return super.read(sink, byteCount).apply {
                if (this == -1L) {
                    totalBytesRead = contentLength()
                } else {
                    totalBytesRead += this
                }
                val progress = (100 * totalBytesRead / contentLength()).toInt()
                Log.d("ProgressResponseBody", "read: progress:$progress")
                if (progress != currentProgress) {
                    currentProgress = progress
                    listener?.onProgress(currentProgress)
                }
                if (totalBytesRead == contentLength()) {
                    listener = null
                }
            }
        }
    })

    override fun contentType() = originalResponseBody.contentType()

    override fun contentLength() = originalResponseBody.contentLength()

    override fun source(): BufferedSource = bufferedSource
}
```

自定义Drawable用来展示加载中的状态。

```k
class ProgressPlaceHolderDrawable(
    private var context: Context,
    private var placeHolderDrawable: Drawable? = null,
    placeHolderId: Int
) : Drawable() {
    private var progress: Int = 0
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val startAngle = 270f
    private val paintStokeWidth = getDensity() * 1.5f
    private val progressPadding = getDensity() * 3.0f

    init {
        if (placeHolderDrawable == null && placeHolderId != 0) {
            placeHolderDrawable = ContextCompat.getDrawable(context, placeHolderId)
        }
        paint.color = Color.GRAY
        paint.strokeWidth = paintStokeWidth
    }

    override fun setBounds(bounds: Rect) {
        super.setBounds(bounds)
        placeHolderDrawable?.bounds = bounds
    }

    override fun setTint(tintColor: Int) {
        super.setTint(tintColor)
        paint.color = tintColor
    }

    fun setProgress(@androidx.annotation.IntRange(from = 0, to = 100) progress: Int) {
        this.progress = progress
        invalidateSelf()
    }

    override fun draw(canvas: Canvas) {
        placeHolderDrawable?.draw(canvas)
        val centerX = (bounds.width() ushr 1).toFloat()
        val centerY = (bounds.height() ushr 1).toFloat()
        var radius = (min(bounds.width(), bounds.height()) ushr 1).toFloat()
        val dp30 = getDensity() * 30
        if (radius > dp30 * 1.25) {
            radius = dp30
        } else {
            radius *= 0.85f
        }
        paint.style = Paint.Style.STROKE
        canvas.drawCircle(centerX, centerY, radius, paint)
        paint.style = Paint.Style.FILL
        val endAngle = progress / 100f * 360
        val recF = RectF(
            centerX - radius + progressPadding,
            centerY - radius + progressPadding,
            centerX + radius - progressPadding,
            centerY + radius - progressPadding
        )
        canvas.drawArc(recF, startAngle, endAngle, true, paint)
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
        invalidateSelf()
    }

    override fun getOpacity() = PixelFormat.TRANSLUCENT

    private fun getDensity() = context.resources.displayMetrics.density
}
```

为了将自定义的drawable应用在框架中，需要扩展BaseRequestOptions的api。自定义@GlideExtension注解修饰的工具类和@GlideOption修饰的静态方法。

```k
@GlideExtension
object ProgressExtension {

    @GlideOption
    @JvmStatic
    fun progress(requestOptions: BaseRequestOptions<*>, context: Context): BaseRequestOptions<*> {
        val progressPlaceHolderDrawable = ProgressPlaceHolderDrawable(
            context,
            requestOptions.placeholderDrawable,
            requestOptions.placeholderId
        )
        progressPlaceHolderDrawable.setTint(Color.GRAY)
        return requestOptions.placeholder(progressPlaceHolderDrawable)
    }
}
```

自定义ImageViewTarget，当下载开始的时候，view注册下载进度的监听。

```k
class ProgressImageViewTarget<T>(private val url: String, imageView: ImageView) :
    ImageViewTarget<T>(imageView) {

    override fun onLoadStarted(placeholder: Drawable?) {
        super.onLoadStarted(placeholder)
        //当开始下载的时候，View注册下载进度的监听。
        if (placeholder is ProgressPlaceHolderDrawable) {
            ProgressInterceptor.addListener(url, object : OnProgressChangeListener {
                override fun onProgress(progress: Int) {
                // ProgressPlaceHodlerDrawable设置progress来更新内容显示加载中的进度。
                    placeholder.setProgress(progress)
                }
            })
        }
    }

    override fun onResourceReady(resource: T, transition: Transition<in T>?) {
        super.onResourceReady(resource, transition)
        ProgressInterceptor.removeListener(url)
    }

    override fun onLoadFailed(errorDrawable: Drawable?) {
        super.onLoadFailed(errorDrawable)
        ProgressInterceptor.removeListener(url)
    }

    override fun onLoadCleared(placeholder: Drawable?) {
        super.onLoadCleared(placeholder)
        ProgressInterceptor.removeListener(url)
    }

    override fun setResource(resource: T?) {
        if (resource is Bitmap) {
            view.setImageBitmap(resource)
        } else if (resource is Drawable) {
            view.setImageDrawable(resource)
        }
    }
}
```

对应的调用代码为：

```k
	GlideApp
            .with(this)
            .load(URL)
            .progress(this)
            .diskCacheStrategy(DiskCacheStrategy.NONE)
            .error(R.color.colorError)
            .into(ProgressImageViewTarget(URL, ivGlide!!))
```



