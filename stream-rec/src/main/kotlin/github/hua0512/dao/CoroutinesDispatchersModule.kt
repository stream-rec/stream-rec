package github.hua0512.dao

import dagger.Module
import dagger.Provides
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

@Module
class CoroutinesDispatchersModule {

  @DefaultDispatcher
  @Provides
  fun providesDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

  @IoDispatcher
  @Provides
  fun providesIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

  @MainDispatcher
  @Provides
  fun providesMainDispatcher(): CoroutineDispatcher = Dispatchers.Main

  @MainImmediateDispatcher
  @Provides
  fun providesMainImmediateDispatcher(): CoroutineDispatcher = Dispatchers.Main.immediate
}