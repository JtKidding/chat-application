let stompClient = null;
let selectedUser = null;
let selectedGroup = null;
let messages = new Map(); // 存儲各個對話的訊息
let groupMessages = new Map(); // 存儲群組對話的訊息
let modalSelectedUser = null;
let modalSearchTimeout;

// 當頁面載入完成時初始化
document.addEventListener('DOMContentLoaded', function() {
    console.log('頁面載入完成，初始化聊天...');
    console.log('聊天類型:', chatType);
    console.log('群組ID:', groupId);

    connect();
    setupEventListeners();

    if (chatType === 'group' && groupId) {
        // 如果是群組聊天，自動載入群組訊息
        selectedGroup = parseInt(groupId);
        console.log('設置選中群組:', selectedGroup);
        loadGroupChatHistory(groupId);
    } else if (chatType === 'private') {
        // 載入用戶的最後訊息預覽
        loadMessagePreviews();
    }

    const modalUserSearch = document.getElementById('modalUserSearch');
    if (modalUserSearch) {
        modalUserSearch.addEventListener('input', function() {
            clearTimeout(modalSearchTimeout);
            const query = this.value.trim();

            if (query.length >= 2) {
                modalSearchTimeout = setTimeout(() => searchModalUsers(query), 300);
            } else {
                hideModalSearchResults();
            }
        });
    }

    // 點擊彈窗外部關閉
    document.getElementById('inviteModal').addEventListener('click', function(e) {
        if (e.target === this) {
            closeInviteModal();
        }
    });
    // 檢查是否有之前選中的用戶
    // const lastSelectedUser = localStorage.getItem('selectedUser');
    // if (lastSelectedUser) {
    //     // 等待用戶列表載入完成後自動選擇
    //     setTimeout(() => {
    //         const userElement = document.querySelector(`[data-username="${lastSelectedUser}"]`);
    //         if (userElement) {
    //             selectUser(userElement);
    //         }
    //     }, 1000);
    // }
});

// 建立WebSocket連接
function connect() {
    const socket = new SockJS('/ws');
    stompClient = Stomp.over(socket);

    stompClient.connect({}, function(frame) {
        console.log('Connected: ' + frame);

        if (chatType === 'private') {
            console.log('訂閱私人訊息');
            // 訂閱個人訊息
            stompClient.subscribe('/user/queue/messages', function(message) {
                console.log('收到私人訊息原始數據:', message.body);
                const messageData = JSON.parse(message.body);
                // handleIncomingMessage(messageData);
                handleIncomingPrivateMessage(messageData);
            });
        } else if (chatType === 'group') {
            console.log('訂閱群組訊息，群組ID:', groupId);
            // 訂閱群組訊息
            stompClient.subscribe('/user/queue/group-messages', function(message) {
                console.log('收到群組訊息原始數據:', message.body);
                const messageData = JSON.parse(message.body);
                handleIncomingGroupMessage(messageData);
            });

            // 訂閱群組更新
            stompClient.subscribe('/user/queue/group-updates', function(message) {
                console.log('收到群組更新:', message.body);
                const updateData = JSON.parse(message.body);
                handleGroupUpdate(updateData);
            });

            // 加入群組
            if (groupId) {
                console.log('發送加入群組訊息:', groupId);
                stompClient.send("/app/chat.joinGroup", {}, JSON.stringify({
                    groupId: groupId.toString()
                }));
            }
        }

        // 訂閱公共訊息（用戶狀態變化）
        stompClient.subscribe('/topic/public', function(message) {
            const messageData = JSON.parse(message.body);
            handleUserStatusChange(messageData);
        });

        // 通知伺服器用戶已上線
        console.log('發送用戶上線通知:', currentUser);
        stompClient.send("/app/chat.addUser", {}, JSON.stringify({
            username: currentUser
        }));

    }, function(error) {
        console.error('WebSocket connection error: ', error);
        // 5秒後重新連接
        setTimeout(connect, 5000);
    });
}

// 設置事件監聽器
function setupEventListeners() {
    const messageInput = document.getElementById('messageInput');
    const sendButton = document.getElementById('sendButton');

    if (messageInput && sendButton) {
        // 發送按鈕點擊事件
        sendButton.addEventListener('click', sendMessage);

        // 輸入框按下Enter鍵發送
        messageInput.addEventListener('keypress', function(e) {
            if (e.key === 'Enter') {
                sendMessage();
            }
        });
    }

    // 頁面關閉時斷開連接
    window.addEventListener('beforeunload', function() {
        if (stompClient) {
            if (chatType === 'group' && groupId) {
                stompClient.send("/app/chat.leaveGroup", {}, JSON.stringify({
                    groupId: groupId.toString()
                }));
            }
            stompClient.send("/app/chat.removeUser", {}, JSON.stringify({
                username: currentUser
            }));
            stompClient.disconnect();
        }
    });
}

// 打開邀請彈窗
function openInviteModal() {
    document.getElementById('inviteModal').style.display = 'flex';
    document.getElementById('modalUserSearch').focus();
}

// 關閉邀請彈窗
function closeInviteModal() {
    document.getElementById('inviteModal').style.display = 'none';
    // 重置表單
    document.getElementById('modalUserSearch').value = '';
    document.getElementById('modalInviteMessage').value = '';
    modalSelectedUser = null;
    document.getElementById('modalInviteBtn').disabled = true;
    hideModalSearchResults();
}

// 搜尋用戶（彈窗版本）
function searchModalUsers(query) {
    fetch(`/api/invitations/search-users?query=${encodeURIComponent(query)}&groupId=${groupId}`)
        .then(response => response.json())
        .then(users => {
            displayModalSearchResults(users);
        })
        .catch(error => {
            console.error('搜尋用戶失敗:', error);
        });
}

// 顯示搜尋結果（彈窗版本）
function displayModalSearchResults(users) {
    const resultsContainer = document.getElementById('modalSearchResults');

    if (users.length === 0) {
        resultsContainer.innerHTML = '<div style="padding: 1rem; text-align: center; color: #666;">沒有找到匹配的用戶</div>';
    } else {
        resultsContainer.innerHTML = users.map(user => `
            <div class="search-result-item" onclick="selectModalUser(${user.id}, '${user.username}', '${user.displayName}', '${user.avatarUrl}')">
                <img src="${user.avatarUrl}" alt="頭像" class="search-result-avatar">
                <div class="search-result-info">
                    <div class="search-result-name">${user.displayName}</div>
                    <div class="search-result-username">@${user.username}</div>
                </div>
                ${user.isOnline ? '<span style="color: #28a745;">●</span>' : '<span style="color: #6c757d;">●</span>'}
            </div>
        `).join('');
    }

    resultsContainer.style.display = 'block';
}

// 選擇用戶（彈窗版本）
function selectModalUser(id, username, displayName, avatarUrl) {
    modalSelectedUser = { id, username, displayName, avatarUrl };
    document.getElementById('modalUserSearch').value = `${displayName} (@${username})`;
    document.getElementById('modalInviteBtn').disabled = false;
    hideModalSearchResults();
}

// 隱藏搜尋結果（彈窗版本）
function hideModalSearchResults() {
    document.getElementById('modalSearchResults').style.display = 'none';
}

// 發送邀請（彈窗版本）
function sendModalInvitation() {
    if (!modalSelectedUser) {
        alert('請先選擇要邀請的用戶');
        return;
    }

    const message = document.getElementById('modalInviteMessage').value.trim();
    const inviteBtn = document.getElementById('modalInviteBtn');

    // 禁用按鈕
    inviteBtn.textContent = '發送中...';
    inviteBtn.disabled = true;

    const requestData = {
        groupId: groupId,
        inviteeUsername: modalSelectedUser.username,
        message: message
    };

    fetch('/api/invitations/send', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
        },
        body: JSON.stringify(requestData)
    })
        .then(response => response.json())
        .then(data => {
            if (data.success) {
                alert(`✅ 邀請已發送給 ${modalSelectedUser.displayName}`);
                closeInviteModal();
            } else {
                alert('❌ 發送邀請失敗: ' + data.error);
            }
        })
        .catch(error => {
            console.error('發送邀請錯誤:', error);
            alert('發送邀請失敗，請稍後再試');
        })
        .finally(() => {
            // 恢復按鈕
            inviteBtn.textContent = '發送邀請';
            inviteBtn.disabled = modalSelectedUser === null;
        });
}

// 私人聊天：選擇用戶開始聊天
function selectUser(userElement) {
    if (chatType !== 'private') return;

    // 移除之前選中用戶的樣式
    document.querySelectorAll('.user-item').forEach(item => {
        item.classList.remove('selected');
    });

    // 添加選中樣式
    userElement.classList.add('selected');

    // 獲取選中用戶資訊
    selectedUser = userElement.dataset.username;
    selectedGroup = null;
    const userDisplayName = userElement.querySelector('.user-name').textContent;
    const userAvatar = userElement.querySelector('.avatar-img').src;

    // 更新聊天標題
    document.getElementById('chatHeader').innerHTML = `
        <div style="display: flex; align-items: center; gap: 15px;">
            <img src="${userAvatar}" alt="用戶頭像" style="width: 40px; height: 40px; border-radius: 50%; object-fit: cover;">
            <h3>與 ${userDisplayName} 的對話</h3>
        </div>
    `;

    // 顯示訊息輸入區域
    document.getElementById('messageInputContainer').style.display = 'block';

    // 清除該用戶的未讀狀態
    clearUnreadStatus(selectedUser);

    // 載入聊天歷史
    loadChatHistory(selectedUser);

    /*
    // 先檢查本地是否有快取的訊息
    if (messages.has(selectedUser)) {
        const messagesContainer = document.getElementById('messagesContainer');
        messagesContainer.innerHTML = '';

        const cachedMessages = messages.get(selectedUser);
        if (cachedMessages.length === 0) {
            messagesContainer.innerHTML = '<div class="no-messages">開始你們的對話吧！</div>';
        } else {
            cachedMessages.forEach(message => {
                displayMessage(message);
            });
        }
    } else {
        // 如果沒有快取，從服務器載入
        loadChatHistory(selectedUser);
    }
    */

    // 記住選中的用戶
    // localStorage.setItem('selectedUser', selectedUser);

    // 清空輸入框
    document.getElementById('messageInput').value = '';
    document.getElementById('messageInput').focus();

    // 標記該用戶的訊息為已讀（調用後端API）
    markMessagesAsRead(selectedUser);
}

// 新增函數：清除未讀狀態
function clearUnreadStatus(username) {
    const userItems = document.querySelectorAll('.user-item');
    userItems.forEach(item => {
        if (item.dataset.username === username) {
            // 移除未讀CSS類
            item.classList.remove('has-unread');

            // 更新預覽訊息樣式
            const previewElement = item.querySelector('.message-preview');
            if (previewElement) {
                previewElement.style.fontWeight = 'normal';
                previewElement.style.color = '#999';
                previewElement.classList.remove('unread');
            }

            // 如果有預覽容器，也要更新
            const previewContainer = item.querySelector('.preview-container');
            if (previewContainer) {
                const preview = previewContainer.querySelector('.message-preview');
                if (preview) {
                    preview.style.fontWeight = 'normal';
                    preview.style.color = '#999';
                    preview.classList.remove('unread');
                }
            }
        }
    });
}

// 新增函數：標記訊息為已讀
function markMessagesAsRead(username) {
    fetch(`/api/messages/${username}/mark-read`, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
        }
    })
        .then(response => {
            if (response.ok) {
                console.log(`已標記與 ${username} 的訊息為已讀`);
            }
        })
        .catch(error => {
            console.error('標記訊息已讀失敗:', error);
        });
}

// 發送訊息
function sendMessage() {
    const messageInput = document.getElementById('messageInput');
    const messageContent = messageInput.value.trim();

    console.log('發送訊息:', {
        content: messageContent,
        chatType: chatType,
        selectedUser: selectedUser,
        groupId: groupId,
        stompConnected: stompClient ? stompClient.connected : false
    });

    if (!messageContent || !stompClient) {
        console.warn('訊息內容為空或 WebSocket 未連接');
        return;
    }

    if (chatType === 'private' && selectedUser) {
        // 發送私人訊息
        const message = {
            recipientUsername: selectedUser,
            content: messageContent
        };

        stompClient.send("/app/chat.sendMessage", {}, JSON.stringify(message));
    } else if (chatType === 'group' && groupId) {
        // 發送群組訊息
        const message = {
            groupId: groupId.toString(),
            content: messageContent
        };
        stompClient.send("/app/chat.sendGroupMessage", {}, JSON.stringify(message));
    } else {
        console.error('無效的聊天配置:', { chatType, selectedUser, groupId });
        return;
    }

    messageInput.value = '';
    messageInput.focus();
}

// 處理接收到的私人訊息
function handleIncomingPrivateMessage(messageData) {
    if (messageData.messageType !== 'private') return;

    const chatPartner = messageData.senderUsername === currentUser ?
        messageData.recipientUsername : messageData.senderUsername;

    // 存儲訊息到對應的對話中
    if (!messages.has(chatPartner)) {
        messages.set(chatPartner, []);
    }

    // 檢查是否是重複訊息（避免重複顯示）
    const existingMessages = messages.get(chatPartner);
    const isDuplicate = existingMessages.some(msg =>
        msg.id === messageData.id ||
        (msg.content === messageData.content &&
            Math.abs(new Date(msg.timestamp) - new Date(messageData.timestamp)) < 1000)
    );

    if (!isDuplicate) {
        existingMessages.push(messageData);

        /*
        // 如果是新的接收訊息且不是當前聊天對象，設置為未讀
        if (messageData.senderUsername !== currentUser && selectedUser !== chatPartner) {
            messageData.isRead = false;
        } else {
            // 如果是當前聊天對象，自動標記為已讀
            messageData.isRead = true;
        }
         */

        // 更新用戶列表中的最後訊息預覽
        updateUserPreview(chatPartner, messageData);
    }

    // 如果當前正在與此用戶聊天，顯示訊息
    if (selectedUser === chatPartner && !isDuplicate) {
        displayMessage(messageData);

        /*
        // 如果是接收到的訊息，自動標記為已讀
        if (messageData.senderUsername !== currentUser) {
            markMessagesAsRead(chatPartner);
        }
         */
    }

    // 播放通知音效（可選）
    if (messageData.senderUsername !== currentUser && selectedUser !== chatPartner) {
        playNotificationSound();
    }
}

// 處理接收到的群組訊息
function handleIncomingGroupMessage(messageData) {
    console.log('收到群組訊息:', messageData);

    if (messageData.messageType !== 'group') {
        console.warn('訊息類型不是群組訊息:', messageData.messageType);
        return;
    }

    const messageGroupId = parseInt(messageData.groupId);
    console.log('訊息群組ID:', messageGroupId, '當前群組ID:', selectedGroup);

    // 存儲訊息到對應的群組中
    if (!groupMessages.has(messageGroupId)) {
        groupMessages.set(messageGroupId, []);
    }

    const existingMessages = groupMessages.get(messageGroupId);
    const isDuplicate = existingMessages.some(msg =>
        msg.id === messageData.id ||
        (msg.content === messageData.content &&
            Math.abs(new Date(msg.timestamp) - new Date(messageData.timestamp)) < 1000)
    );

    if (!isDuplicate) {
        existingMessages.push(messageData);
    }

    // 如果當前正在此群組聊天，顯示訊息
    if (selectedGroup === messageGroupId && !isDuplicate) {
        console.log('顯示群組訊息');
        displayGroupMessage(messageData);
    } else {
        console.log('不顯示訊息:', {
            selectedGroup,
            messageGroupId,
            isDuplicate,
            shouldDisplay: selectedGroup === messageGroupId && !isDuplicate
        });
    }

    // 播放通知音效
    if (messageData.senderUsername !== currentUser) {
        playNotificationSound();
    }
}

// 處理群組更新
function handleGroupUpdate(updateData) {
    console.log('群組更新:', updateData);

    if (updateData.type === 'USER_ONLINE') {
        updateGroupMemberStatus(updateData.username, true);
    } else if (updateData.type === 'USER_OFFLINE') {
        updateGroupMemberStatus(updateData.username, false);
    }
}

// 更新群組成員狀態
function updateGroupMemberStatus(username, isOnline) {
    const memberItems = document.querySelectorAll('.user-item');
    memberItems.forEach(item => {
        const nameElement = item.querySelector('.user-name');
        if (nameElement && nameElement.textContent.includes(username)) {
            const statusElement = item.querySelector('.user-status');
            const indicatorElement = item.querySelector('.online-indicator');

            if (statusElement && !statusElement.textContent.includes('創建者')) {
                statusElement.textContent = isOnline ? '線上' : '離線';
            }

            if (indicatorElement) {
                indicatorElement.className = `online-indicator ${isOnline ? 'online' : 'offline'}`;
            }
        }
    });
}

// 顯示群組訊息
function displayGroupMessage(messageData) {
    console.log('顯示群組訊息:', messageData);

    const messagesContainer = document.getElementById('messagesContainer');
    if (!messagesContainer) {
        console.error('找不到訊息容器');
        return;
    }
    const messageElement = document.createElement('div');

    const isSystemMessage = messageData.messageType === 'SYSTEM' ||
                                    messageData.senderUsername === 'system' ||
                                    !messageData.senderUsername;

    const isSent = messageData.senderUsername === currentUser && !isSystemMessage;

    if (isSystemMessage) {
        messageElement.className = 'message system-message';
        messageElement.innerHTML = `
            <div class="system-content">
                <span class="system-icon">ℹ️</span>
                <span class="system-text">${escapeHtml(messageData.content)}</span>
            </div>
        `;
    } else {
        messageElement.className = `message ${isSent ? 'sent' : 'received'}`;

        const timestamp = new Date(messageData.timestamp).toLocaleTimeString('zh-TW', {
            hour: '2-digit',
            minute: '2-digit'
        });

        let avatarHtml = '';
        if (!isSent && messageData.senderAvatarUrl) {
            avatarHtml = `
                <div class="message-avatar">
                    <img src="${messageData.senderAvatarUrl}" alt="頭像" 
                         style="width: 30px; height: 30px; border-radius: 50%; margin-right: 10px;">
                </div>
            `;
        }

        const senderName = messageData.senderDisplayName || messageData.senderUsername || '未知用戶';

        messageElement.innerHTML = `
            ${avatarHtml}
            <div class="message-content">
                ${!isSent ? `<div class="sender-name">${escapeHtml(senderName)}</div>` : ''}
                <div class="message-text">${escapeHtml(messageData.content)}</div>
                <div class="message-info">${timestamp}</div>
            </div>
        `;
    }

    messagesContainer.appendChild(messageElement);
    messagesContainer.scrollTop = messagesContainer.scrollHeight;

    console.log('訊息已添加到 DOM');
}

// 顯示訊息在聊天界面
function displayMessage(messageData) {
    const messagesContainer = document.getElementById('messagesContainer');
    const messageElement = document.createElement('div');

    const isSent = messageData.senderUsername === currentUser;
    messageElement.className = `message ${isSent ? 'sent' : 'received'}`;

    const timestamp = new Date(messageData.timestamp).toLocaleTimeString('zh-TW', {
        hour: '2-digit',
        minute: '2-digit'
    });

    // 如果是接收的訊息，顯示發送者頭像
    let avatarHtml = '';
    if (!isSent && messageData.senderAvatarUrl) {
        avatarHtml = `
            <div class="message-avatar">
                <img src="${messageData.senderAvatarUrl}" alt="頭像" 
                    style="width: 30px; height: 30px; border-radius: 50%; margin-right: 10px;">
            </div>
        `;
    }

    messageElement.innerHTML = `
        ${avatarHtml}
        <div class="message-content">
            <div class="message-text">${escapeHtml(messageData.content)}</div>
            <div class="message-info">${timestamp}</div>
        </div>
    `;

    messagesContainer.appendChild(messageElement);
    messagesContainer.scrollTop = messagesContainer.scrollHeight;
}

// 載入聊天歷史記錄（從伺服器獲取）
function loadChatHistory(username) {
    console.log('Loading chat history for:', username);
    console.log('API URL:', `/api/messages/${username}`);

    const messagesContainer = document.getElementById('messagesContainer');
    messagesContainer.innerHTML = '<div class="loading">載入中...</div>';

    fetch(`/api/messages/${username}`)
        .then(response => {
            if (!response.ok) {
                throw new Error('Network response was not ok');
            }
            return response.json();
        })
        .then(messagesData => {
            messagesContainer.innerHTML = '';

            if (messagesData.length === 0) {
                messagesContainer.innerHTML = '<div class="no-messages">開始你們的對話吧！</div>';
            } else {
                messagesData.forEach(message => {
                    displayMessage(message);
                });
                // 存儲到本地以便快速訪問
                messages.set(username, messagesData);
            }
        })
        .catch(error => {
            console.error('載入聊天記錄失敗:', error);
            messagesContainer.innerHTML = '<div class="error-message">載入聊天記錄失敗，請重試</div>';
        });
}

// 載入群組聊天歷史記錄
function loadGroupChatHistory(groupId) {
    const messagesContainer = document.getElementById('messagesContainer');
    messagesContainer.innerHTML = '<div class="loading">載入中...</div>';

    fetch(`/api/groups/${groupId}/messages`)
        .then(response => {
            if (!response.ok) {
                throw new Error('Network response was not ok');
            }
            return response.json();
        })
        .then(messagesData => {
            messagesContainer.innerHTML = '';

            if (messagesData.length === 0) {
                messagesContainer.innerHTML = '<div class="no-messages">還沒有群組訊息，開始聊天吧！</div>';
            } else {
                messagesData.forEach(message => {
                    displayGroupMessage(message);
                });
                groupMessages.set(parseInt(groupId), messagesData);
            }
        })
        .catch(error => {
            console.error('載入群組聊天記錄失敗:', error);
            messagesContainer.innerHTML = '<div class="error-message">載入群組聊天記錄失敗，請重試</div>';
        });
}

// 處理用戶狀態變化
function handleUserStatusChange(messageData) {
    if (messageData.type === 'JOIN') {
        console.log('用戶上線: ' + messageData.sender);
        updateUserOnlineStatus(messageData.sender, true);
        showStatusNotification(`${messageData.displayName || messageData.sender} 上線了`, 'success');
    } else if (messageData.type === 'LEAVE') {
        console.log('用戶下線: ' + messageData.sender);
        updateUserOnlineStatus(messageData.sender, false);
        showStatusNotification(`${messageData.displayName || messageData.sender} 下線了`, 'info');
    }
}

// 更新用戶在線狀態
function updateUserOnlineStatus(username, isOnline) {
    const userItems = document.querySelectorAll('.user-item');
    userItems.forEach(item => {
        if (item.dataset.username === username) {
            const statusElement = item.querySelector('.user-status');
            const indicatorElement = item.querySelector('.online-indicator');

            statusElement.textContent = isOnline ? '線上' : '離線';
            statusElement.style.color = isOnline ? '#27ae60' : '#666';

            if (indicatorElement) {
                indicatorElement.className = `online-indicator ${isOnline ? 'online' : 'offline'}`;
            }
        }
    });
}

// 載入訊息預覽
function loadMessagePreviews() {
    fetch('/api/messages/latest')
        .then(response => {
            if (!response.ok) {
                throw new Error('Network response was not ok');
            }
            return response.json();
        })
        .then(latestMessages => {
            // 為每個用戶更新最後訊息預覽
            Object.keys(latestMessages).forEach(username => {
                const messageData = latestMessages[username];
                updateUserPreview(username, messageData);
            });
        })
        .catch(error => {
            console.error('載入訊息預覽失敗:', error);
        });
}

// 更新用戶預覽（顯示最後一條訊息）
function updateUserPreview(username, messageData) {
    const userItems = document.querySelectorAll('.user-item');
    userItems.forEach(item => {
        if (item.dataset.username === username) {
            let previewContainer = item.querySelector('.preview-container');
            if (!previewContainer) {
                previewContainer = document.createElement('div');
                previewContainer.className = 'preview-container';
                previewContainer.style.display = 'flex';
                previewContainer.style.justifyContent = 'space-between';
                previewContainer.style.alignItems = 'center';
                previewContainer.style.marginTop = '2px';
                item.querySelector('.user-info').appendChild(previewContainer);
            }

            let previewElement = item.querySelector('.message-preview');
            if (!previewElement) {
                previewElement = document.createElement('div');
                previewElement.className = 'message-preview';
                previewContainer.appendChild(previewElement);
            }

            let timeElement = previewContainer.querySelector('.message-time');
            if (!timeElement) {
                timeElement = document.createElement('div');
                timeElement.className = 'message-time';
                previewContainer.appendChild(timeElement);
            }

            const preview = messageData.content.length > 20 ?
                messageData.content.substring(0, 20) + '...' : messageData.content;

            previewElement.textContent = messageData.senderUsername === currentUser ?
                `你: ${preview}` : preview;

            // 顯示時間（如 "14:30" 或 "昨天"）
            const messageTime = new Date(messageData.timestamp);
            const now = new Date();
            const diffHours = (now - messageTime) / (1000 * 60 * 60);

            if (diffHours < 24) {
                timeElement.textContent = messageTime.toLocaleTimeString('zh-TW', {
                    hour: '2-digit',
                    minute: '2-digit'
                });
            } else if (diffHours < 48) {
                timeElement.textContent = '昨天';
            } else {
                timeElement.textContent = messageTime.toLocaleDateString('zh-TW', {
                    month: 'short',
                    day: 'numeric'
                });
            }

            // 設置未讀樣式 - 只有在非當前選中用戶且為未讀時才設置
            const isUnread = messageData.senderUsername !== currentUser && !messageData.isRead;
            const isCurrentlySelected = selectedUser === username;

            if (isUnread && !isCurrentlySelected) {
                previewElement.style.fontWeight = 'bold';
                previewElement.style.color = '#667eea';
                previewElement.classList.add('unread');
                item.classList.add('has-unread');
            } else {
                previewElement.style.fontWeight = 'normal';
                previewElement.style.color = '#999';
                previewElement.classList.remove('unread');
                item.classList.remove('has-unread');
            }
        }
    });
}

// 顯示狀態通知
function showStatusNotification(message, type = 'info') {
    const notification = document.createElement('div');
    notification.className = `status-notification ${type}`;
    notification.textContent = message;
    notification.style.cssText = `
        position: fixed;
        top: 20px;
        right: 20px;
        padding: 10px 15px;
        border-radius: 5px;
        color: white;
        font-size: 14px;
        z-index: 1000;
        animation: slideIn 0.3s ease-out;
    `;

    if (type === 'success') {
        notification.style.background = '#27ae60';
    } else if (type === 'info') {
        notification.style.background = '#3498db';
    }

    document.body.appendChild(notification);

    // 3秒後自動移除
    setTimeout(() => {
        notification.style.animation = 'slideOut 0.3s ease-out';
        setTimeout(() => {
            if (document.body.contains(notification)) {
                document.body.removeChild(notification);
            }
        }, 300);
    }, 3000);
}

// 播放通知音效
function playNotificationSound() {
    try {
        // 創建音效（使用Web Audio API）
        const audioContext = new (window.AudioContext || window.webkitAudioContext)();
        const oscillator = audioContext.createOscillator();
        const gainNode = audioContext.createGain();

        oscillator.connect(gainNode);
        gainNode.connect(audioContext.destination);

        oscillator.frequency.value = 800;
        oscillator.type = 'sine';

        gainNode.gain.setValueAtTime(0.1, audioContext.currentTime);
        gainNode.gain.exponentialRampToValueAtTime(0.01, audioContext.currentTime + 0.1);

        oscillator.start(audioContext.currentTime);
        oscillator.stop(audioContext.currentTime + 0.1);
    } catch (error) {
        console.log('無法播放通知音效:', error);
    }
}

// HTML轉義函數，防止XSS攻擊
function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

// 斷開連接
function disconnect() {
    if (stompClient !== null) {
        stompClient.disconnect();
    }
    console.log("Disconnected");
}

// 重新連接功能
function reconnect() {
    console.log('嘗試重新連接...');
    disconnect();
    setTimeout(connect, 1000);
}

// 格式化時間戳
function formatTimestamp(timestamp) {
    const date = new Date(timestamp);
    const now = new Date();
    const diffMs = now - date;
    const diffHours = diffMs / (1000 * 60 * 60);
    const diffDays = diffMs / (1000 * 60 * 60 * 24);

    if (diffHours < 1) {
        const diffMinutes = Math.floor(diffMs / (1000 * 60));
        return diffMinutes <= 1 ? '剛才' : `${diffMinutes}分鐘前`;
    } else if (diffHours < 24) {
        return date.toLocaleTimeString('zh-TW', {
            hour: '2-digit',
            minute: '2-digit'
        });
    } else if (diffDays < 7) {
        const days = Math.floor(diffDays);
        return days === 1 ? '昨天' : `${days}天前`;
    } else {
        return date.toLocaleDateString('zh-TW', {
            month: 'short',
            day: 'numeric'
        });
    }
}

// 滾動到底部
function scrollToBottom() {
    const messagesContainer = document.getElementById('messagesContainer');
    if (messagesContainer) {
        messagesContainer.scrollTop = messagesContainer.scrollHeight;
    }
}

// 檢查是否需要自動滾動
function shouldAutoScroll() {
    const messagesContainer = document.getElementById('messagesContainer');
    if (!messagesContainer) return false;

    const { scrollTop, scrollHeight, clientHeight } = messagesContainer;
    return scrollTop + clientHeight >= scrollHeight - 100; // 100px 容差
}

// 添加CSS動畫樣式
const style = document.createElement('style');
style.textContent = `
    @keyframes slideIn {
        from {
            transform: translateX(100%);
            opacity: 0;
        }
        to {
            transform: translateX(0);
            opacity: 1;
        }
    }
    
    @keyframes slideOut {
        from {
            transform: translateX(0);
            opacity: 1;
        }
        to {
            transform: translateX(100%);
            opacity: 0;
        }
    }
    
    .message-avatar {
        display: flex;
        align-items: flex-end;
    }
    
    .message.received {
        align-items: flex-end;
    }
    
    .sender-name {
        font-size: 0.8rem;
        color: #667eea;
        font-weight: 600;
        margin-bottom: 0.25rem;
    }
    
    .system-message {
        justify-content: center;
        margin: 0.5rem 0;
    }
    
    .system-content {
        background: #f0f8ff;
        border: 1px solid #b3d9ff;
        border-radius: 12px;
        padding: 0.5rem 1rem;
        font-size: 0.9rem;
        color: #0066cc;
        display: flex;
        align-items: center;
        gap: 0.5rem;
    }
    
    .system-icon {
        font-size: 1rem;
    }
    
    .message-preview.unread {
        font-weight: bold !important;
        color: #667eea !important;
    }
    
    .user-item.has-unread {
        background: linear-gradient(90deg, #f0f8ff 0%, #ffffff 100%);
        border-left: 3px solid #667eea;
    }
    
    .user-item.has-unread .user-name {
        font-weight: 600;
    }
    
    .user-item.selected.has-unread {
        background: #e3f2fd;
        border-left: 4px solid #667eea;
    }
    
    .user-item.selected .message-preview {
        font-weight: normal !important;
        color: #999 !important;
    }
    
    .loading {
        text-align: center;
        color: #666;
        padding: 20px;
        font-style: italic;
    }
    
    .no-messages {
        text-align: center;
        color: #666;
        font-style: italic;
        margin-top: 50px;
        padding: 20px;
    }
    
    .error-message {
        text-align: center;
        color: #e74c3c;
        padding: 20px;
        background: #ffe6e6;
        border-radius: 8px;
        margin: 20px;
        border: 1px solid #ffb3b3;
    }
`;
document.head.appendChild(style);

// 全局錯誤處理
window.addEventListener('error', function(event) {
    console.error('JavaScript錯誤:', event.error);
});

// WebSocket錯誤處理
function handleWebSocketError(error) {
    console.error('WebSocket錯誤:', error);
    showStatusNotification('連接失敗，正在嘗試重新連接...', 'error');
    setTimeout(reconnect, 3000);
}

// 檢查網絡連接
function checkNetworkStatus() {
    if (!navigator.onLine) {
        showStatusNotification('網絡連接已斷開', 'error');
    } else if (!stompClient || !stompClient.connected) {
        showStatusNotification('正在重新連接...', 'info');
        reconnect();
    }
}

// 監聽網絡狀態變化
window.addEventListener('online', function() {
    showStatusNotification('網絡連接已恢復', 'success');
    if (!stompClient || !stompClient.connected) {
        reconnect();
    }
});

window.addEventListener('offline', function() {
    showStatusNotification('網絡連接已斷開', 'error');
});

// 定期檢查連接狀態
setInterval(checkNetworkStatus, 30000); // 每30秒檢查一次

console.log('Chat.js 載入完成，聊天類型:', chatType);