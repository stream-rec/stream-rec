package github.hua0512.dao

import dagger.Module
import dagger.Provides
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton


@Module
class CoroutineModule {

  @Provides
  @Singleton
  @ApplicationScope
  fun provideApplicationScope(
    @DefaultDispatcher defaultDispatcher: CoroutineDispatcher,
  ): CoroutineScope = CoroutineScope(SupervisorJob() + defaultDispatcher)

  @Provides
  @Singleton
  @IoDispatcher
  fun provideIoScope(
    @IoDispatcher ioDispatcher: CoroutineDispatcher,
  ): CoroutineScope = CoroutineScope(SupervisorJob() + ioDispatcher)
}