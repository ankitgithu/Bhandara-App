package com.aistudio.bhandara.gwlmbd.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.aistudio.bhandara.gwlmbd.R
import com.aistudio.bhandara.gwlmbd.ui.theme.DeepRed
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 16:9 Image Slider / Banner Carousel for the very top of the Home Screen.
 * Displays attached images exactly as provided without cropping or stretching.
 * Features smooth right-to-left transition, auto-slide every 4 seconds,
 * subtle swipe gesture support, and animated pagination indicator dots.
 */
@Composable
fun HomeBannerSlider(
    modifier: Modifier = Modifier,
    autoScrollDelayMs: Long = 4000L
) {
    val banners = remember {
        listOf(
            R.drawable.banner_home_1,
            R.drawable.banner_home_2,
            R.drawable.banner_home_3
        )
    }

    if (banners.isEmpty()) return

    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { banners.size }
    )
    val coroutineScope = rememberCoroutineScope()
    val isUserDragging by pagerState.interactionSource.collectIsDraggedAsState()

    // Auto-slide loop: runs continuously unless the user is actively dragging
    LaunchedEffect(isUserDragging, pagerState.currentPage) {
        if (!isUserDragging && banners.size > 1) {
            delay(autoScrollDelayMs)
            val nextPage = (pagerState.currentPage + 1) % banners.size
            pagerState.animateScrollToPage(
                page = nextPage,
                animationSpec = tween(
                    durationMillis = 650,
                    easing = FastOutSlowInEasing
                )
            )
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Horizontal Pager for 16:9 Banner Slides
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            pageSpacing = 10.dp
        ) { page ->
            val bannerRes = banners[page]

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = bannerRes),
                        contentDescription = "Bhandara Banner ${page + 1}",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(16.dp)),
                        contentScale = ContentScale.Fit
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Animated Pagination Indicator Dots
        if (banners.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                banners.forEachIndexed { index, _ ->
                    val isSelected = pagerState.currentPage == index
                    val dotWidth by animateDpAsState(
                        targetValue = if (isSelected) 20.dp else 6.dp,
                        animationSpec = tween(durationMillis = 280),
                        label = "dotWidth"
                    )
                    val dotColor by animateColorAsState(
                        targetValue = if (isSelected) DeepRed else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                        animationSpec = tween(durationMillis = 280),
                        label = "dotColor"
                    )

                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .height(6.dp)
                            .width(dotWidth)
                            .clip(CircleShape)
                            .background(dotColor)
                            .clickable {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(
                                        page = index,
                                        animationSpec = tween(
                                            durationMillis = 500,
                                            easing = FastOutSlowInEasing
                                        )
                                    )
                                }
                            }
                    )
                }
            }
        }
    }
}
