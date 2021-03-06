/*
 * Copyright 2013 Google Inc. All Rights Reserved.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.signintest;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

public class SignInUser {

	/**
	 * HashMap used to store the providers registered.
	 */
	private HashMap<Provider,Object> mProviderData;
	
	/**
	 * The user's name.
	 */
	private String mName;
	
	/**
	 * The application ID (which may map to several IDP ids).
	 */
	private Long mId;
	
	/**
	 * Flag whether this is a newly created user.
	 */
	private boolean mIsNew;
	
	/**
	 * The SQLite DB we're using to keep app account information.
	 */
	private DBAdapter mDb;
	
	/**
	 * Create a sign in user. Does not take ownership of the DBAdapter.
	 * @param db
	 */
	public SignInUser(DBAdapter db) {
		mDb = db;
		mProviderData = new HashMap<Provider,Object>();
	}
	
	public void setProviderData(Provider provider, Object user) {
		mProviderData.put(provider, user);
		if (mId == null) {
			mId = mDb.getUserId(provider, this);
			if(mId == null) {
				// There is no user in the DB, so create one.
				mId = mDb.createUser(provider, this);
				mIsNew = true;
			} else {
				mIsNew = false;
			}
		} else {
			mDb.associateUser(provider, this, mId.longValue());
		}
	}
	
	public void removeProvider(Provider provider) {
		mDb.deleteProviderUser(provider, this);
		mProviderData.remove(provider);
	}
	
	public Object getProviderData(Provider provider) {
		return mProviderData.get(provider);
	}
	
	public boolean isSignedIn() {
		return !mProviderData.isEmpty();
	}
	
	public boolean isNew() {
		return mIsNew;
	}

	public void setName(String name) {
		mName = name;
	}
	
	public String getName() {
		return mName;
	}
	
	/**
	 * Retrieve the account identifier.
	 * @return Long the local account ID.
	 */
	public Long getId() {
		return mId;
	}
	
	public String getProviderUserId(Provider provider) {
		return provider.getUserId(getProviderData(provider));
	}
	
	public boolean hasFeature(ProviderUtil.Feature feature) {
		for(Provider p : mProviderData.keySet()) {
			if (p.hasFeature(feature)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Check whether there is overlap in the connected providers between 
	 * the users.
	 * 
	 * @param user
	 * @return boolean whether the two users can be merged
	 */
	public boolean canMerge(SignInUser user) {
		Set<Provider> a = listConnectedProviders();
		Set<Provider> b = user.listConnectedProviders();
		if (user.getId() == getId()) {
			return true;
		}
		for(Provider p : a) {
			if (b.contains(p)) {
				return false;
			}
		}
		return true;
	}
	
	/**
	 * Delete the account user, and any associated IDP identities.
	 */
	public void delete() {
		mDb.deleteUser(mId);
	}
	
	/**
	 * Combine the supplied user with the current one, and delete the passed in 
	 * user. 
	 * 
	 * @param user
	 */
	public void merge(SignInUser user) {
		// Remove the old user first to ensure the DB is clear.
		if(user.getId() != mId) {
			user.delete();
		}
		for(Provider provider : user.listConnectedProviders()) {
			// If we have not synchronised users, we should do that.
			if(!mProviderData.containsKey(provider)) {
				// If we have already sychronised, then only update DB if the user differs.
				if(user.getId() != mId) {
					setProviderData(provider, user.getProviderData(provider));
				} else {
					mProviderData.put(provider, user);
				}
			}
		}
	}

	public Set<Provider> listConnectedProviders() {
		return mProviderData.keySet();
	}

	/**
	 * Helper function to list IDP accounts which we expect to be 
	 * associated with this account, but are not currently connected.
	 * 
	 * @return Set<String> Set of the provider IDs.
	 */
	public Set<String> listAdditionalProviders() {
		Set<String> retval = new HashSet<String>();
		for(String name : mDb.getConnectedProviders(mId)) {
			boolean found = false;
			for(Provider p : listConnectedProviders()) {
				if(p.getId().equals(name)) {
					found = true;
				}
			}
			if(!found) {
				retval.add(name);
			}
		}
		return retval;
	}

}
