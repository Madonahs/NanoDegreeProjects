package com.madonasyombua.bakingapp.fragment

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.Fragment

import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.SimpleOnItemTouchListener
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import butterknife.BindView
import butterknife.ButterKnife
import butterknife.Unbinder
import com.madonasyombua.bakingapp.R
import com.madonasyombua.bakingapp.adapters.RecipesAdapter
import com.madonasyombua.bakingapp.databinding.FragmentRecipesBinding
import com.madonasyombua.bakingapp.helpers.ApiCallback
import com.madonasyombua.bakingapp.helpers.Listeners.OnItemClickListener
import com.madonasyombua.bakingapp.helpers.RecipesApiManager.Companion.instance
import com.madonasyombua.bakingapp.models.Recipe
import com.madonasyombua.bakingapp.utils.Prefs
import com.madonasyombua.bakingapp.utils.SpacingItemDecoration
import com.madonasyombua.bakingapp.widget.AppWidgetService
import com.orhanobut.logger.Logger
import java.util.*

class RecipeFragment : Fragment(R.layout.fragment_recipes) {
  //  @BindView(R.id.recipes_recycler_view)
  //  var recipesRecyclerView: RecyclerView? = null

   // @BindView(R.id.pull_to_refresh)
   // var swipeRefreshLayout: SwipeRefreshLayout? = null

   // @BindView(R.id.noDataContainer)


    var constraintLayout: ConstraintLayout? = null
    private var mListener: OnRecipeClickListener? = null
    private var unbinder: Unbinder? = null
    private var mRecipes: List<Recipe?>? = null

    private var binding: FragmentRecipesBinding? = null

    private val networkChangeReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (mRecipes == null) {
                loadingRecipe()
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding = FragmentRecipesBinding.bind(view)
        // Inflate the layout for this fragment bind view to butter knife

        binding?.pullToRefresh?.setOnRefreshListener { loadingRecipe() }
        constraintLayout?.visibility = View.VISIBLE
        setupRecyclerView()
        if (savedInstanceState != null && savedInstanceState.containsKey(RECIPES_KEY)) {
            mRecipes = savedInstanceState.getParcelableArrayList(RECIPES_KEY)
           binding?.recipesRecyclerView?.adapter = activity?.applicationContext?.let {
                RecipesAdapter(it, mRecipes as List<Recipe>,
                        object : OnItemClickListener {
                            override fun onItemClick(position: Int) {
                                mListener?.onRecipeSelected(mRecipes!![position])
                            }
                        })
            }
            dataLoadedTakeCareLayout()
        }

    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        mListener = if (context is OnRecipeClickListener) {
            context
        } else {
            throw RuntimeException(context.toString()
                    + " must implement OnRecipeClickListener")
        }
    }

    override fun onDetach() {
        super.onDetach()
        mListener = null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        unbinder!!.unbind()
        Logger.d("onDestroyView")
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    override fun onResume() {
        super.onResume()
        activity?.registerReceiver(networkChangeReceiver,
                IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION))
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    override fun onPause() {
        super.onPause()
        activity?.unregisterReceiver(networkChangeReceiver)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (mRecipes != null && !mRecipes?.isEmpty()!!) outState.putParcelableArrayList(RECIPES_KEY, mRecipes as ArrayList<out Parcelable?>?)
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    private fun setupRecyclerView() {
        binding?.recipesRecyclerView?.visibility = View.GONE
        binding?.recipesRecyclerView?.setHasFixedSize(true)
        val twoPaneMode = false
        if (twoPaneMode) {
            binding?.recipesRecyclerView?.layoutManager = GridLayoutManager(activity?.applicationContext, 3)
        } else {
            binding?.recipesRecyclerView?.layoutManager = LinearLayoutManager(activity?.applicationContext, LinearLayoutManager.VERTICAL, false)
        }
        binding?.recipesRecyclerView?.addItemDecoration(SpacingItemDecoration(resources.getDimension(R.dimen.margin_medium).toInt()))
        binding?.recipesRecyclerView?.addOnItemTouchListener(SimpleOnItemTouchListener())
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    private fun loadingRecipe() {
        if (activity?.applicationContext?.let { isNetworkAvailable(it) }!!) {
            binding?.pullToRefresh?.isRefreshing = true
            instance?.getRecipes(object : ApiCallback<List<Recipe?>?> {
                override fun onResponse(result: List<Recipe?>?) {
                    if (result != null) {
                        mRecipes = result
                        binding?.recipesRecyclerView?.adapter = RecipesAdapter(activity!!.applicationContext, mRecipes as List<Recipe>, object : OnItemClickListener {
                            override fun onItemClick(position: Int) {
                                mListener?.onRecipeSelected(mRecipes!![position])
                            }
                        })
                        // Set the default recipe for the widget
                        if (this@RecipeFragment.activity?.applicationContext?.let { Prefs.loadRecipe(it) } == null) {
                            AppWidgetService.updateWidget(activity, mRecipes!![0])
                        }
                    } else {
                        Toast.makeText(context, "failed to load data", Toast.LENGTH_SHORT).show()
                    }
                    dataLoadedTakeCareLayout()
                }

                override fun onCancel() {
                    dataLoadedTakeCareLayout()
                }
            })
        } else {
            Toast.makeText(context, "No Internet", Toast.LENGTH_SHORT).show()
        }
    }

    private fun dataLoadedTakeCareLayout() {
        val loaded = mRecipes != null && mRecipes?.size!! > 0
      binding?.pullToRefresh?.isRefreshing = false
        binding?.recipesRecyclerView?.visibility = if (loaded) View.VISIBLE else View.GONE
        constraintLayout?.visibility = if (loaded) View.GONE else View.VISIBLE
    }

    /**
     * This interface must be implemented by activities that contain this
     * fragment to allow an interaction in this fragment to be communicated
     * to the activity and potentially other fragments contained in that
     * activity.
     *
     *
     * See the Android Training lesson [Communicating with Other Fragments](http://developer.android.com/training/basics/fragments/communicating.html) for more information.
     */
    interface OnRecipeClickListener {
        fun onRecipeSelected(recipe: Recipe?)
    }

    companion object {
        private const val RECIPES_KEY = "recipes"

        /**
         * check if the network is available
         * @param context context
         * @return connection
         */
        @Suppress("DEPRECATION")
        fun isNetworkAvailable(context: Context): Boolean {
            val cm = (context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager)
            return cm.activeNetworkInfo != null && cm.activeNetworkInfo?.isConnected!!
        }
    }
    
}