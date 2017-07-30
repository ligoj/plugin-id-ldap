define(function () {
	var current = {

		configureSubscriptionParameters: function (configuration) {
			current.registerIdParentGroupSelect2(configuration, 'service:id:parent-group');
			current.registerIdGroupSelect2(configuration, 'service:id:group');
			current.registerIdOuSelect2(configuration, 'service:id:ou');
		},

		/**
		 * Render LDAP.
		 */
		renderFeatures: function (subscription) {
			var result = '';
			var now = moment();

			// Add activity export
			result += '<div class="btn-group btn-link" data-container="body" data-toggle="tooltip" title="' + current.$messages['export'] + '">';
			result += ' <i class="fa fa-download" data-toggle="dropdown"></i>';
			result += ' <ul class="dropdown-menu dropdown-menu-right"><li>';
			result += current.$super('renderServicelink')('file-excel-o', REST_PATH + 'service/id/ldap/activity/' + subscription.id + '/group-' + subscription.parameters['service:id:group'] + '-' + now.format('YYYY-MM-DD') + '.csv', undefined, 'service:id:activity-group', ' download');
			result += current.$super('renderServicelink')('file-excel-o', REST_PATH + 'service/id/ldap/activity/' + subscription.id + '/project-' + subscription.parameters['service:id:group'] + '-' + now.format('YYYY-MM-DD') + '.csv', undefined, 'service:id:activity-project', ' download');
			result += '</li></ul></div>';
			return result;
		},

		/**
		 * Replace the default text rendering by a Select2 for Customers/OU.
		 */
		registerIdOuSelect2: function (configuration, id) {
			configuration.validators[id] = current.validateIdOuCreateMode;
			current.$super('registerXServiceSelect2')(configuration, id, 'service/id/ldap/customer/', undefined, true);
		},
		/**
		 * Replace the default text rendering by a Select2 for ParentGroup.
		 */
		registerIdParentGroupSelect2: function (configuration, id) {
			configuration.validators[id] = current.validateIdGroupCreateMode;
			current.$super('registerXServiceSelect2')(configuration, id, 'service/id/group', '?search[value]=');
		},

		/**
		 * Replace the input by a select2 in link mode. In creation mode, disable manual edition of 'group',
		 * and add a simple text with live
		 * validation regarding existing group and syntax.
		 */
		registerIdGroupSelect2: function (configuration, id) {
			var cProviders = configuration.providers['form-group'];
			var previousProvider = cProviders[id] || cProviders.standard;
			if (configuration.mode === 'create') {
				cProviders[id] = function (parameter, container, $input) {
					// Register a live validation of group
					var simpleGroupId = 'service:id:group-simple-name';
					configuration.validators[simpleGroupId] = current.validateIdGroupCreateMode;

					// Disable computed parameters and remove the description, since it is overridden
					var parentParameter = $.extend({}, parameter);
					parentParameter.description = null;
					var $fieldset = previousProvider(parentParameter, container, $input).parent();
					$input.attr('readonly', 'readonly');

					// Create the input corresponding to the last part of the final group name
					var $simpleInput = $('<input class="form-control" type="text" id="' + simpleGroupId + '" required autocomplete="off">');
					cProviders.standard({
						id: simpleGroupId,
						mandatory: true
					}, $fieldset, $simpleInput);
				};
			} else {
				current.$super('registerXServiceSelect2')(configuration, id, 'service/id/ldap/group/');
			}
		},

		/**
		 * Live validation of LDAP OU.
		 */
		validateIdOuCreateMode: function () {
			var $input = _('service:id:ou');
			validationManager.reset($input);
			var data = $input.select2('data');
			if (data && data['new']) {
				// Organization will be created
				validationManager.addWarn($input, {
					rule: 'service:id:ou-not-exists'
				}, 'service:id:ou', true);
			} else if (data) {
				// Existing organization
				validationManager.addSuccess($input, [], null, true);
			}

			// Propagate the validation
			return current.validateIdGroupCreateMode();
		},

		/**
		 * Live validation of LDAP group, OU and parent.
		 */
		validateIdGroupCreateMode: function () {
			validationManager.reset(_('service:id:group'));
			var $input = _('service:id:group');
			var simpleName = _('service:id:group-simple-name').val();
			var organisation = _('service:id:ou').val();
			var parent = _('service:id:parent-group').val();
			var fullName = (parent ? parent + '-' : (organisation ? organisation + '-' : '')) + (simpleName || '').toLowerCase();
			$input.val(fullName).closest('.form-group').find('.form-control-feedback').remove().end().addClass('has-feedback');
			if (fullName !== current.$super('model').pkey && !fullName.startsWith(current.$super('model').pkey + '-')) {
				validationManager.addError($input, {
					rule: 'StartsWith',
					parameters: current.$super('model').pkey
				}, 'group', true);
				return false;
			}
			// Live validation to check the group does not exists
			validationManager.addMessage($input, null, [], null, 'fa fa-refresh fa-spin');
			$.ajax({
				dataType: 'json',
				url: REST_PATH + 'service/id/group/' + encodeURIComponent(fullName) + '/exists',
				type: 'GET',
				success: function (data) {
					if (data) {
						// Existing project
						validationManager.addError(_('service:id:group'), {
							rule: 'already-exist',
							parameters: ['service:id:group', fullName]
						}, 'group', true);
					} else {
						// Succeed, not existing project
						validationManager.addSuccess($input, [], null, true);
					}
				}
			});

			// For now return true for the immediate validation system, even if the Ajax call may fail
			return true;
		}
	};
	return current;
});
