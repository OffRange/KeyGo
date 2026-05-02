package de.davis.keygo.core.item.presentation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Work
import de.davis.keygo.core.item.domain.model.Vault

fun Vault.Icon.toImageVector() = when (this) {
    Vault.Icon.Person -> Icons.Default.Person
    Vault.Icon.Home -> Icons.Default.Home
    Vault.Icon.Favorite -> Icons.Default.Favorite
    Vault.Icon.Work -> Icons.Default.Work
    Vault.Icon.Business -> Icons.Default.Business
    Vault.Icon.School -> Icons.Default.School
    Vault.Icon.MenuBook -> Icons.AutoMirrored.Default.MenuBook
    Vault.Icon.Lock -> Icons.Default.Lock
    Vault.Icon.Computer -> Icons.Default.Computer
    Vault.Icon.PhoneAndroid -> Icons.Default.PhoneAndroid
    Vault.Icon.AccountBalanceWallet -> Icons.Default.AccountBalanceWallet
    Vault.Icon.CreditCard -> Icons.Default.CreditCard
    Vault.Icon.ShoppingCart -> Icons.Default.ShoppingCart
    Vault.Icon.Flight -> Icons.Default.Flight
    Vault.Icon.Hotel -> Icons.Default.Hotel
    Vault.Icon.DirectionsCar -> Icons.Default.DirectionsCar
    Vault.Icon.Restaurant -> Icons.Default.Restaurant
    Vault.Icon.LocalCafe -> Icons.Default.LocalCafe
    Vault.Icon.FitnessCenter -> Icons.Default.FitnessCenter
    Vault.Icon.MusicNote -> Icons.Default.MusicNote
    Vault.Icon.SportsEsports -> Icons.Default.SportsEsports
    Vault.Icon.Star -> Icons.Default.Star
}
