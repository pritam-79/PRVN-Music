package com.maxrave.simpmusic.ui.screen.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.maxrave.simpmusic.model.AppLogo
import com.maxrave.simpmusic.model.AppLogos
import com.maxrave.simpmusic.ui.component.EndOfPage
import com.maxrave.simpmusic.ui.component.RippleIconButton
import com.maxrave.simpmusic.ui.icon.ArrowBackIosNew
import com.maxrave.simpmusic.ui.icon.Check
import com.maxrave.simpmusic.ui.icon.SimpIcons
import com.maxrave.simpmusic.ui.theme.typo
import com.maxrave.simpmusic.viewModel.SharedViewModel
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.viewmodel.koinViewModel

private val BrandPurple = Color(0xFFA855F7)
private val BrandCyan = Color(0xFF06B6D4)
private val CardBg = Color(0xFF141923)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppIconScreen(
    navController: NavController,
    paddingValues: PaddingValues = PaddingValues(),
    sharedViewModel: SharedViewModel = koinViewModel(),
) {
    val activeLogoId by sharedViewModel.appIcon.collectAsStateWithLifecycle(AppLogos.DEFAULT_LOGO_ID)
    val currentLogo = AppLogos.find(activeLogoId)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "App Icon",
                        style = typo().titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    RippleIconButton(
                        imageVector = SimpIcons.ArrowBackIosNew,
                        onClick = { navController.navigateUp() },
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                ),
            )
        },
        containerColor = Color(0xFF0B0E14),
    ) { innerPadding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 150.dp),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = innerPadding.calculateTopPadding() + 8.dp,
                bottom = paddingValues.calculateBottomPadding() + 48.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            // Header: Active Icon Preview & Reset button
            item(span = { GridItemSpan(maxLineSpan) }) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CardBg),
                    border = BorderStroke(1.dp, BrandPurple.copy(alpha = 0.4f)),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Image(
                            painter = painterResource(currentLogo.drawable),
                            contentDescription = currentLogo.name,
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(14.dp)),
                            contentScale = ContentScale.Crop,
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Active Launcher Icon",
                                style = typo().labelSmall,
                                color = BrandCyan,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = currentLogo.name,
                                style = typo().titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = currentLogo.id,
                                style = typo().bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (currentLogo.id != AppLogos.DEFAULT_LOGO_ID) {
                            Button(
                                onClick = { sharedViewModel.setAppIcon(AppLogos.DEFAULT_LOGO_ID) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = BrandPurple,
                                    contentColor = Color.White,
                                ),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            ) {
                                Text(
                                    text = "Reset",
                                    style = typo().labelMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
            }

            // Section 1 Header: Reference Sheet 2 (Neon Series)
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)) {
                    Text(
                        text = "Sheet 2 — Neon & Audio Series",
                        style = typo().titleSmall,
                        color = BrandPurple,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Minimal neon default and acoustic identity designs",
                        style = typo().bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            val sheet2Logos = AppLogos.ALL.filter { it.id.startsWith("prvn_logo_1") }
            items(sheet2Logos, key = { it.id }) { logo ->
                LogoGridCard(
                    logo = logo,
                    isSelected = logo.id == activeLogoId,
                    onClick = { sharedViewModel.setAppIcon(logo.id) },
                )
            }

            // Section 2 Header: Reference Sheet 1 (Classic & Special Series)
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(modifier = Modifier.padding(top = 20.dp, bottom = 4.dp)) {
                    Text(
                        text = "Sheet 1 — Classic & Special Series",
                        style = typo().titleSmall,
                        color = BrandCyan,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Aurora, vinyl, waveform, infinity sound & special liquid glass",
                        style = typo().bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            val sheet1Logos = AppLogos.ALL.filter { !it.id.startsWith("prvn_logo_1") || it.id == "prvn_logo_10" || it.id == "prvn_logo_11" || it.id == "prvn_logo_12" }
            items(sheet1Logos, key = { it.id }) { logo ->
                LogoGridCard(
                    logo = logo,
                    isSelected = logo.id == activeLogoId,
                    onClick = { sharedViewModel.setAppIcon(logo.id) },
                )
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                EndOfPage()
            }
        }
    }
}

@Composable
private fun LogoGridCard(
    logo: AppLogo,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val borderColor = when {
        isSelected -> BrandPurple
        logo.isSpecial -> BrandCyan.copy(alpha = 0.5f)
        else -> Color.White.copy(alpha = 0.08f)
    }
    val borderWidth = if (isSelected) 2.dp else 1.dp

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = BorderStroke(borderWidth, borderColor),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(logo.drawable),
                    contentDescription = logo.name,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(14.dp)),
                    contentScale = ContentScale.Crop,
                )

                // Selection checkmark badge
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(26.dp)
                            .background(BrandPurple, CircleShape)
                            .border(1.5.dp, Color.White, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = SimpIcons.Check,
                            contentDescription = "Selected",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }

                // Default badge
                if (logo.isDefault) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(6.dp)
                            .background(BrandPurple.copy(alpha = 0.9f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = "DEFAULT",
                            style = TextStyle(
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            ),
                        )
                    }
                }

                // Special badge
                if (logo.isSpecial) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(6.dp)
                            .background(BrandCyan.copy(alpha = 0.9f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = "SPECIAL",
                            style = TextStyle(
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black,
                            ),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = logo.name,
                style = typo().bodyMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (isSelected) BrandPurple else MaterialTheme.colorScheme.onSurface,
            )

            Text(
                text = logo.id,
                style = typo().bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
            )
        }
    }
}
