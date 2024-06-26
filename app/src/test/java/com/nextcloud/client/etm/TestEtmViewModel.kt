/*
 * Nextcloud - Android Client
 *
 * SPDX-FileCopyrightText: 2020 Chris Narkiewicz <hello@ezaquarii.com>
 * SPDX-License-Identifier: AGPL-3.0-or-later OR GPL-2.0-only
 */
package com.nextcloud.client.etm

import android.accounts.AccountManager
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import com.nextcloud.client.account.MockUser
import com.nextcloud.client.account.UserAccountManager
import com.nextcloud.client.etm.pages.EtmBackgroundJobsFragment
import com.nextcloud.client.jobs.BackgroundJobManager
import com.nextcloud.client.jobs.JobInfo
import com.nextcloud.client.migrations.MigrationsDb
import com.nextcloud.client.migrations.MigrationsManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Suite
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.reset
import org.mockito.kotlin.same
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(Suite::class)
@Suite.SuiteClasses(
    TestEtmViewModel.MainPage::class,
    TestEtmViewModel.PreferencesPage::class,
    TestEtmViewModel.BackgroundJobsPage::class
)
class TestEtmViewModel {

    internal abstract class Base {

        @get:Rule
        val rule = InstantTaskExecutorRule()

        protected lateinit var context: Context
        protected lateinit var platformAccountManager: AccountManager
        protected lateinit var accountManager: UserAccountManager
        protected lateinit var sharedPreferences: SharedPreferences
        protected lateinit var vm: EtmViewModel
        protected lateinit var resources: Resources
        protected lateinit var backgroundJobManager: BackgroundJobManager
        protected lateinit var migrationsManager: MigrationsManager
        protected lateinit var migrationsDb: MigrationsDb

        @Before
        fun setUpBase() {
            context = mock()
            sharedPreferences = mock()
            platformAccountManager = mock()
            accountManager = mock()
            resources = mock()
            backgroundJobManager = mock()
            migrationsManager = mock()
            migrationsDb = mock()
            whenever(resources.getString(any())).thenReturn("mock-account-type")
            whenever(accountManager.user).thenReturn(MockUser())
            vm = EtmViewModel(
                context,
                sharedPreferences,
                platformAccountManager,
                accountManager,
                resources,
                backgroundJobManager,
                migrationsManager,
                migrationsDb
            )
        }
    }

    internal class MainPage : Base() {

        @Test
        fun `current page is not set`() {
            // GIVEN
            //      main page is displayed
            // THEN
            //      current page is null
            assertNull(vm.currentPage.value)
        }

        @Test
        fun `back key is not handled`() {
            // GIVEN
            //      main page is displayed
            // WHEN
            //      back key is pressed
            val handled = vm.onBackPressed()

            // THEN
            //      is not handled
            assertFalse(handled)
        }

        @Test
        fun `page is selected`() {
            val observer: Observer<EtmMenuEntry?> = mock()
            val selectedPageIndex = 0
            val expectedPage = vm.pages[selectedPageIndex]

            // GIVEN
            //      main page is displayed
            //      current page observer is registered
            vm.currentPage.observeForever(observer)
            reset(observer)

            // WHEN
            //      page is selected
            vm.onPageSelected(selectedPageIndex)

            // THEN
            //      current page is set
            //      page observer is called once with selected entry
            assertNotNull(vm.currentPage.value)
            verify(observer, times(1)).onChanged(same(expectedPage))
        }

        @Test
        fun `out of range index is ignored`() {
            val maxIndex = vm.pages.size
            // GIVEN
            //      observer is registered
            val observer: Observer<EtmMenuEntry?> = mock()
            vm.currentPage.observeForever(observer)
            reset(observer)

            // WHEN
            //      out of range page index is selected
            vm.onPageSelected(maxIndex + 1)

            // THEN
            //      nothing happens
            verify(observer, never()).onChanged(anyOrNull())
            assertNull(vm.currentPage.value)
        }
    }

    internal class PreferencesPage : Base() {

        @Before
        fun setUp() {
            vm.onPageSelected(0)
        }

        @Test
        fun `back goes back to main page`() {
            val observer: Observer<EtmMenuEntry?> = mock()

            // GIVEN
            //      a page is selected
            //      page observer is registered
            assertNotNull(vm.currentPage.value)
            vm.currentPage.observeForever(observer)

            // WHEN
            //      back is pressed
            val handled = vm.onBackPressed()

            // THEN
            //      back press is handled
            //      observer is called with null page
            assertTrue(handled)
            verify(observer).onChanged(eq(null))
        }

        @Test
        fun `back is handled only once`() {
            // GIVEN
            //      a page is selected
            assertNotNull(vm.currentPage.value)

            // WHEN
            //      back is pressed twice
            val first = vm.onBackPressed()
            val second = vm.onBackPressed()

            // THEN
            //      back is handled only once
            assertTrue(first)
            assertFalse(second)
        }

        @Test
        fun `preferences are loaded from shared preferences`() {
            // GIVEN
            //      shared preferences contain values of different types
            val preferenceValues: Map<String, Any> = mapOf(
                "key1" to 1,
                "key2" to "value2",
                "key3" to false
            )
            whenever(sharedPreferences.all).thenReturn(preferenceValues)

            // WHEN
            //      vm preferences are read
            val prefs = vm.preferences

            // THEN
            //      all preferences are converted to strings
            assertEquals(preferenceValues.size, prefs.size)
            assertEquals("1", prefs["key1"])
            assertEquals("value2", prefs["key2"])
            assertEquals("false", prefs["key3"])
        }
    }

    internal class BackgroundJobsPage : Base() {
        @Before
        fun setUp() {
            vm.onPageSelected(EtmViewModel.PAGE_JOBS)
            assertEquals(EtmBackgroundJobsFragment::class, vm.currentPage.value?.pageClass)
        }

        @Test
        fun `prune jobs action is delegated to job manager`() {
            vm.pruneJobs()
            verify(backgroundJobManager).pruneJobs()
        }

        @Test
        fun `start stop test job actions are delegated to job manager`() {
            vm.startTestJob(true)
            vm.cancelTestJob()
            inOrder(backgroundJobManager).apply {
                verify(backgroundJobManager).scheduleTestJob()
                verify(backgroundJobManager).cancelTestJob()
            }
        }

        @Test
        fun `job info is taken from job manager`() {
            val jobInfo: LiveData<List<JobInfo>> = mock()
            whenever(backgroundJobManager.jobs).thenReturn(jobInfo)
            assertSame(jobInfo, vm.backgroundJobs)
        }
    }
}
