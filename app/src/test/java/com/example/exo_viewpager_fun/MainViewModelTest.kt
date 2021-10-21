package com.example.exo_viewpager_fun

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.Closeable

class MainViewModelTest {
    @get:Rule
    val rule = CoroutinesTestRule()

    @Test
    fun `should create new player when non exist`() = mainViewModel {
        initPlayer()

        assertPlayerCreatedCount(1)
    }

    @Test
    fun `should not create new player when one exists`() = mainViewModel {
        initPlayer()
        initPlayer()

        assertPlayerCreatedCount(1)
    }

    @Test
    fun `should create new player when cached is torn down`() = mainViewModel {
        initPlayer()
        tearDown()
        initPlayer()

        assertPlayerCreatedCount(2)
    }

    @Test
    fun `should tear down player when view model is torn down`() = mainViewModel {
        initPlayer()
        tearDown()

        assertPlayerReleased()
    }

    @Test
    fun `should save player state when view model is torn down`() = mainViewModel {
        val playerState = PlayerState(
            currentMediaItemId = "id",
            seekPositionMillis = 60L,
            isPlaying = false
        )
        initPlayer()
        setPlayerState(playerState)
        tearDown()

        assertPlayerStateSaved(playerState)
    }

    @Test
    fun `should hide player when media position is changed`() = mainViewModel(
        isPlayerRendering = flowOf(true)
    ) {
        initPlayer()
        setCurrentMediaIndex(7)
        changeMediaPosition(42)

        assertShowPlayer(false)
    }

    @Test
    fun `should not hide player when media position change to same position is attempted`() = mainViewModel(
        isPlayerRendering = flowOf(true)
    ) {
        initPlayer()
        setCurrentMediaIndex(7)
        changeMediaPosition(7)

        assertShowPlayer(true)
    }

    @Test
    fun `should show player when player starts rendering`() {
        val isPlayerRendering = MutableStateFlow(false)
        mainViewModel(isPlayerRendering = isPlayerRendering) {
            initPlayer()
            isPlayerRendering.value = true

            assertShowPlayer(true)
        }
    }

    @Test
    fun `should emit video data when repository emits video data`() = mainViewModel {
        emitVideoData(TEST_VIDEO_DATA)

        assertCachedVideoData(TEST_VIDEO_DATA)
    }

    @Test
    fun `should setup app player when repository emits video data`() = mainViewModel {
        initPlayer()
        val videoData = TEST_VIDEO_DATA + listOf(VideoData(mediaUri = "asdf", previewImageUri = "png"))
        emitVideoData(videoData)

        assertPlayerSetupWith(videoData)
    }

    @Test
    fun `should setup app player when player is requested and cached video data exists`() = mainViewModel(
        videoData = TEST_VIDEO_DATA
    ) {
        initPlayer()

        assertPlayerSetupWith(TEST_VIDEO_DATA)
    }

    @Test
    fun `should pause player when tapped while playing`() = mainViewModel(
        initialState = PlayerState.INITIAL.copy(isPlaying = true)
    ) {
        initPlayer()

        tapPlayer()

        assertPlaying(false)
    }

    @Test
    fun `should play player when tapped while paused`() = mainViewModel(
        initialState = PlayerState.INITIAL.copy(isPlaying = false)
    ) {
        initPlayer()

        tapPlayer()

        assertPlaying(true)
    }

    fun mainViewModel(
        initialState: PlayerState = PlayerState.INITIAL,
        videoData: List<VideoData> = TEST_VIDEO_DATA,
        isPlayerRendering: Flow<Boolean> = emptyFlow(),
        block: MainViewModelRobot.() -> Unit
    ) {
        MainViewModelRobot(initialState, videoData, isPlayerRendering).use(block)
    }

    class MainViewModelRobot(
        initialState: PlayerState,
        videoData: List<VideoData>,
        isPlayerRendering: Flow<Boolean>
    ) : Closeable {
        private val appPlayer = FakeAppPlayer(isPlayerRendering).apply {
            currentPlayerState = initialState
        }
        private val appPlayerFactory = FakeAppPlayer.Factory(appPlayer)
        private val handle = PlayerSavedStateHandle(
            handle = SavedStateHandle()
        ).apply { set(initialState) }
        private val videoDataFlow = MutableStateFlow(videoData)
        private val viewModel = MainViewModel(
            repository = FakeVideoDataRepository(videoDataFlow),
            appPlayerFactory = appPlayerFactory,
            handle = handle
        )

        fun initPlayer() {
            viewModel.getPlayer()
        }

        fun tearDown() {
            viewModel.tearDown()
        }

        fun setPlayerState(playerState: PlayerState) {
            appPlayer.currentPlayerState = playerState
        }

        fun setCurrentMediaIndex(index: Int) {
            appPlayer.currentPlayerState = appPlayer.currentPlayerState.copy(currentMediaIndex = index)
        }

        fun changeMediaPosition(position: Int) {
            viewModel.playMediaAt(position)
        }

        fun emitVideoData(videoData: List<VideoData>) {
            videoDataFlow.value = videoData
        }

        fun tapPlayer() {
            viewModel.onPlayerTapped()
        }

        fun assertPlayerCreatedCount(times: Int) {
            assertEquals(times, appPlayerFactory.createCount)
        }

        fun assertPlayerReleased() {
            assertTrue(appPlayer.didRelease)
        }

        fun assertPlayerStateSaved(playerState: PlayerState) {
            assertEquals(playerState, handle.get())
        }

        fun assertShowPlayer(value: Boolean) {
            assertEquals(value, viewModel.viewStates().value.showPlayer)
        }

        fun assertCachedVideoData(videoData: List<VideoData>) {
            assertEquals(videoData, viewModel.viewStates().value.videoData)
        }

        fun assertPlayerSetupWith(videoData: List<VideoData>) {
            assertEquals(videoData, appPlayer.setups.last())
        }

        fun assertPlaying(isPlaying: Boolean) {
            assertEquals(isPlaying, appPlayer.currentPlayerState.isPlaying)
        }

        override fun close() {
            viewModel.tearDown()
        }
    }
}
