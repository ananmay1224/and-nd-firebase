/**
 * Copyright Google Inc. All Rights Reserved.
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.udacity.friendlychat;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.firebase.ui.auth.AuthUI;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.udacity.friendlychat.MessageAdapter;
import com.google.firebase.udacity.friendlychat.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    public static final String ANONYMOUS = "anonymous";
    public static final int DEFAULT_MSG_LENGTH_LIMIT = 1000;
    public static final int RC_SIGN_IN = 1;

    private ListView tmpMessageListView;
    private MessageAdapter tmpMessageAdapter;
    private ProgressBar tmpProgressBar;
    private EditText tmpMessageEditText;
    private Button tmpSendButton;

    private String tmpUsername;

    //Firebase instance variables
    private FirebaseDatabase firebaseDatabase;
    private DatabaseReference tmpMessagesDatabaseReference;
    private ChildEventListener tmpChildEventListener;
    private FirebaseAuth tmpFirebaseAuth;
    private FirebaseAuth.AuthStateListener tmpAuthStateListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tmpUsername = ANONYMOUS;

        firebaseDatabase = FirebaseDatabase.getInstance();
        tmpFirebaseAuth = FirebaseAuth.getInstance();

        tmpMessagesDatabaseReference = firebaseDatabase.getReference().child("messages");

        // Initialize references to views
        tmpProgressBar = (ProgressBar) findViewById(R.id.progressBar);
        tmpMessageListView = (ListView) findViewById(R.id.messageListView);
        tmpMessageEditText = (EditText) findViewById(R.id.messageEditText);
        tmpSendButton = (Button) findViewById(R.id.sendButton);

        // Initialize message ListView and its adapter
        List<FriendlyMessage> friendlyMessages = new ArrayList<>();
        tmpMessageAdapter = new MessageAdapter(this, R.layout.item_message, friendlyMessages);
        tmpMessageListView.setAdapter(tmpMessageAdapter);

        // Initialize progress bar
        tmpProgressBar.setVisibility(ProgressBar.INVISIBLE);

        // Enable Send button when there's text to send
        tmpMessageEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                if (charSequence.toString().trim().length() > 0) {
                    tmpSendButton.setEnabled(true);
                } else {
                    tmpSendButton.setEnabled(false);
                }
            }

            @Override
            public void afterTextChanged(Editable editable) {
            }
        });
        tmpMessageEditText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(DEFAULT_MSG_LENGTH_LIMIT)});

        // Send button sends a message and clears the EditText
        tmpSendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // TODO: Send messages on click
                FriendlyMessage friendlyMessage = new FriendlyMessage(tmpMessageEditText.getText().toString(), tmpUsername, null);
                tmpMessagesDatabaseReference.push().setValue(friendlyMessage);

                // Clear input box
                tmpMessageEditText.setText("");
            }
        });

        tmpAuthStateListener = new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
                FirebaseUser user = firebaseAuth.getCurrentUser();
                if (user != null) {
                    ///user is signed in
                    onSignedInInitialize(user.getDisplayName());
                    Toast.makeText(MainActivity.this, "You're now signed in. Welcome to FriendlyChat !", Toast.LENGTH_SHORT).show();
                } else {
                    //user is signed out
                    onSignedOutCleanup();
                    startActivityForResult(
                            AuthUI.getInstance()
                                    .createSignInIntentBuilder()
                                    .setIsSmartLockEnabled(false)
                                    .setAvailableProviders(Arrays.asList(
                                            new AuthUI.IdpConfig.GoogleBuilder().build(),
                                            new AuthUI.IdpConfig.EmailBuilder().build(),
                                            new AuthUI.IdpConfig.AnonymousBuilder().build()))
                                    .build(),
                            RC_SIGN_IN);
                }
            }
        };
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RC_SIGN_IN) {
            if (requestCode == RESULT_OK) {
                Toast.makeText(this, "Signed in!", Toast.LENGTH_SHORT).show();

            } else if (resultCode == RESULT_CANCELED) {
                Toast.makeText(this, "Sign in canceled", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        tmpFirebaseAuth.addAuthStateListener(tmpAuthStateListener);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (tmpAuthStateListener != null) {
            tmpFirebaseAuth.removeAuthStateListener(tmpAuthStateListener);
        }
        detachDatabaseListener();
        tmpMessageAdapter.clear();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.sign_out_menu:
                //sign out
                AuthUI.getInstance().signOut(this);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void onSignedInInitialize(String username) {
        tmpUsername = username;
        attachDatabaseReadListener();
    }

    private void onSignedOutCleanup() {
        tmpUsername = ANONYMOUS;
        tmpMessageAdapter.clear();
    }

    private void attachDatabaseReadListener() {
        if (tmpChildEventListener == null) {
            tmpChildEventListener = new ChildEventListener() {
                @Override
                //called whenever a new message is triggered in the list
                public void onChildAdded(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {
                    //to get the data of the new message
                    FriendlyMessage friendlyMessage = dataSnapshot.getValue(FriendlyMessage.class);
                    //we add a listener to our messaging list and when it is added, the onChildAdded is called
                    tmpMessageAdapter.add(friendlyMessage);
                }

                @Override
                //called when the contents of an existing message change
                public void onChildChanged(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {
                }

                @Override
                //called when an existing message is deleted
                public void onChildRemoved(@NonNull DataSnapshot dataSnapshot) {
                }

                @Override
                //called if the message changed position in the list
                public void onChildMoved(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {
                }

                @Override
                //called when you do not have permission to read data
                public void onCancelled(@NonNull DatabaseError databaseError) {
                }
            };
            //the reference to find what exactly we are listening to and the listener object defines what will happen to the data
            tmpMessagesDatabaseReference.addChildEventListener(tmpChildEventListener);
        }
    }

    private void detachDatabaseListener() {
        if (tmpChildEventListener != null) {
            tmpMessagesDatabaseReference.removeEventListener(tmpChildEventListener);
            tmpChildEventListener = null;
        }
    }



}
